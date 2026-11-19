# Mutates synthetic onboarding records only on the local-isolation profile and its verified restore.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
$ownerPassword = $env:HRMS_ISOLATION_OWNER_PASSWORD
$dbaPassword = $env:HRMS_ISOLATION_DBA_PASSWORD
if (-not $ownerPassword -or -not $dbaPassword) {
    throw 'Set HRMS_ISOLATION_OWNER_PASSWORD and HRMS_ISOLATION_DBA_PASSWORD.'
}

$username = 'approver@rls-verification.example'
$tenantPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(20))
$code = 'rls-verify-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
$company = @{ name = "RLS Onboarding $code"; code = $code; contactEmail = $username; timezone = 'UTC'; currency = 'USD' }
$owner = $null
$tenant = $null
$previousTenant = $null
$checks = 0

function Call($Method, $Path, $Token, $Body) {
    $options = @{ Uri = "$base$Path"; Method = $Method; SkipHttpErrorCheck = $true; TimeoutSec = 10 }
    if ($Token) { $options.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $options.ContentType = 'application/json'
        $options.Body = ConvertTo-Json -InputObject $Body -Depth 6 -Compress
    }
    Invoke-WebRequest @options
}

function Passed($Label, [bool]$Condition) {
    if (-not $Condition) { throw "$Label failed." }
    $script:checks++
    Write-Host "PASS $Label"
}

function Status($Label, $Response, [int[]]$Expected) {
    Passed "$Label (HTTP $($Response.StatusCode))" ($Response.StatusCode -in $Expected)
}

function Response-Json($Response) {
    $body = $Response.Content
    if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
    ConvertFrom-Json -InputObject $body -NoEnumerate
}

try {
    $signIn = Call Post '/platform/auth/login' $null @{ username = 'logavasu'; password = $ownerPassword }
    Status 'existing platform owner sign-in' $signIn 200
    $owner = (Response-Json $signIn).token
    Status 'owner cannot directly access tenant employee API' (Call Get '/core/employees' $owner) @(401, 403)
    $newCompany = Call Post '/platform/companies' $owner @{ company = $company; adminEmail = $username }
    Status 'owner creates company and first invitation' $newCompany 200
    Passed 'staging sends no external invitation email' ((Response-Json $newCompany).delivered -eq $false)
    $directory = Call Get "/platform/companies?search=$code" $owner
    Status 'owner searches global registry' $directory 200
    $companyId = ((Response-Json $directory).content | Where-Object code -eq $code).id
    Passed 'fixture company was found' ([bool]$companyId)
    $companyPath = "/platform/companies/$companyId"
    $detail = Call Get $companyPath $owner
    Status 'owner reads scoped invitation details' $detail 200
    Passed 'pending invitation and disabled delivery are recorded' (
        (Response-Json $detail).invitation_status -eq 'PENDING' -and (Response-Json $detail).delivery_status -eq 'FAILED')
    Status 'owner revokes invitation' (Call Post "$companyPath/invitation/revoke" $owner) 200
    Passed 'revocation is visible in the detail response' ((Response-Json (Call Get $companyPath $owner)).invitation_status -eq 'REVOKED')
    Status 'owner replaces invitation' (Call Post "$companyPath/invitation/resend" $owner) 200

    # The local profile disables SMTP. Set a known digest on this one test invitation for a controlled acceptance.
    $proofBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    $proof = [Convert]::ToBase64String($proofBytes).TrimEnd('=').Replace('+','-').Replace('/','_')
    $env:HRMS_ISOLATION_TEST_INVITE_HASH = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($proof))).ToLowerInvariant()
    $env:HRMS_ISOLATION_TEST_INVITE_COMPANY = $code
    $env:PGPASSWORD = $dbaPassword
    $sql = @'
\getenv fixture_company HRMS_ISOLATION_TEST_INVITE_COMPANY
\getenv fixture_digest HRMS_ISOLATION_TEST_INVITE_HASH
SELECT current_database()='hrms_isolation_20261119' AND :'fixture_company' ~ '^rls-verify-[0-9a-f]{10}$' AS allowed
\gset
\if :allowed
UPDATE public.company_admin_invitation i SET secret_hash=:'fixture_digest'
FROM public.company c WHERE i.tenant_id=c.id AND c.code=:'fixture_company'
  AND c.status='PENDING' AND i.accepted_at IS NULL AND i.revoked_at IS NULL
RETURNING i.id;
\else
\quit 3
\endif
'@
    $updated = @($sql | & $psql -X -qAt -w -h 127.0.0.1 -p 5432 -U postgres -d hrms_isolation_20261119 -v ON_ERROR_STOP=1)
    if ($LASTEXITCODE -ne 0 -or $updated.Count -ne 1) { throw 'The staging acceptance fixture could not be installed.' }
    $accept = @{ token = $proof; password = $tenantPassword }
    $accepted = Call Post '/auth/accept-admin-invite' $null $accept
    Status 'one-time invitation is accepted' $accepted 200
    Passed 'the acceptance activates the intended company' ((Response-Json $accepted).companyCode -eq $code)
    Status 'an accepted invitation cannot be reused' (Call Post '/auth/accept-admin-invite' $null $accept) 400

    $loginBody = @{ companyCode = $code; username = $username; password = $tenantPassword }
    $first = Call Post '/auth/login' $null $loginBody
    Status 'new company administrator signs in with the existing company flow' $first 200
    $tenant = (Response-Json $first).token
    Status 'new company administrator sees its directory' (Call Get '/core/employees?page=0&size=20' $tenant) 200
    Status 'owner suspends the fixture company' (Call Post "$companyPath/suspend" $owner) 200
    Status 'suspension revokes the previously valid company session' (Call Get '/auth/me' $tenant) 401
    $previousTenant = $tenant
    $tenant = $null
    Status 'owner reactivates the fixture company' (Call Post "$companyPath/reactivate" $owner) 200
    Status 'reactivation does not revive the previous session' (Call Get '/auth/me' $previousTenant) 401
    $reactivated = Call Post '/auth/login' $null $loginBody
    Status 'company administrator can sign in again' $reactivated 200
    $tenant = (Response-Json $reactivated).token
} finally {
    if ($tenant) { Call Post '/auth/logout' $tenant | Out-Null }
    if ($owner) { Call Post '/platform/logout' $owner | Out-Null }
}

Write-Host "$checks onboarding and session checks passed."
