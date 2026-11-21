$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$fixture = $env:HRMS_ISOLATION_FIXTURE_PASSWORD
$password = $env:HRMS_ISOLATION_OWNER_PASSWORD
if (-not $fixture -or -not $password) { throw 'Set the local owner and fixture passwords.' }
$passed = 0
$sessions = @{}

function Request($Method,$Path,[int]$Expected,$Token,$Body) {
    $headers=@{}
    if ($Token) { $headers.Authorization="Bearer $Token" }
    $args=@{Method=$Method;Uri="$base$Path";Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=20}
    if ($null -ne $Body) { $args.ContentType='application/json'; $args.Body=ConvertTo-Json $Body -Depth 5 -Compress }
    $response=Invoke-WebRequest @args
    if ($response.StatusCode -ne $Expected) { throw "$Method $Path returned $($response.StatusCode), expected $Expected." }
    $script:passed++
    return $response
}

function Json($Response) { ConvertFrom-Json -InputObject $Response.Content -NoEnumerate }
function Check($Label,[bool]$Condition) {
    if (-not $Condition) { throw "$Label failed." }
    $script:passed++
    Write-Host "PASS $Label"
}
function SignIn($Username,$Secret,$Company) {
    $body=@{username=$Username;password=$Secret}
    $path='/platform/auth/login'
    if ($Company) { $path='/auth/login'; $body.companyCode=$Company }
    $token=(Json (Request Post $path 200 $null $body)).token
    $sessions[$token]=[bool]$Company
    return $token
}
function Correlated($Owner,$Response) {
    $id=$Response.Headers['X-Request-Id'][0]
    Check 'The request exposes a server-generated correlation ID' ($id -match '^[0-9a-f-]{36}$')
    return (Json (Request Get "/platform/audit?search=$id" 200 $Owner)).events
}

try {
    $owner=SignIn logavasu $password
    $security=SignIn isolation-security-admin $fixture
    $platform=SignIn isolation-platform-admin $fixture
    $employee=SignIn isolation.access.employee $fixture isolation-access
    $anonymous=Request Get '/platform/audit' 401
    $null=Request Get '/platform/audit' 401 $employee
    $deniedPlatform=Request Get '/platform/audit' 403 $platform
    $null=Request Get '/platform/audit' 403 $security
    $deniedCompany=Request Get '/payroll/runs' 403 $employee
    $successCompany=Request Get '/me/profile' 200 $employee

    $main=Request Get '/platform/audit' 200 $owner
    $data=Json $main
    Check 'Audit feed is paginated and disables caching' (
        @($data.events).Count -le 50 -and $data.snapshotEnd -and
        ($main.Headers['Cache-Control'] -join ',') -match 'no-store')
    $snapshot=[uri]::EscapeDataString($data.snapshotEnd)
    $pageTwo=Json (Request Get "/platform/audit?page=1&until=$snapshot" 200 $owner)
    $firstKeys=@($data.events.key)
    Check 'Fixed-snapshot pagination returns unique event keys' (
        @($pageTwo.events | Where-Object { $_.key -in $firstKeys }).Count -eq 0)
    $null=Request Get '/platform/audit?source=RAW' 400 $owner
    $null=Request Get '/platform/audit?page=201' 400 $owner

    $events=Correlated $owner $deniedPlatform
    Check 'Platform authorization denial records the platform actor and role' (
        @($events | Where-Object { $_.source -eq 'PLATFORM' -and $_.action -eq 'AUTHORIZATION_DENIED' -and
            $_.actor_role -eq 'PLATFORM_ADMINISTRATOR' -and $_.outcome -eq 'DENIED' }).Count -eq 1)
    $events=Correlated $owner $anonymous
    Check 'Unauthenticated requests are recorded without using caller-supplied identity' (
        @($events | Where-Object { $_.source -eq 'PLATFORM' -and $_.actor -eq 'anonymous' -and $_.status -eq 401 }).Count -eq 1)
    $events=Correlated $owner $deniedCompany
    Check 'Company permission denial records actor ID, company and action' (
        @($events | Where-Object { $_.source -eq 'COMPANY' -and $_.actor_id -and $_.company -eq 'Access Verification' -and
            $_.operation -eq 'Payroll runs' -and $_.status -eq 403 }).Count -eq 1)
    $events=Correlated $owner $successCompany
    Check 'Successful company request is correlated and retains actor metadata' (
        @($events | Where-Object { $_.source -eq 'COMPANY' -and $_.outcome -eq 'SUCCESS' -and
            $_.actor -eq 'isolation.access.employee' -and $_.actor_id -and $_.actor_role }).Count -eq 1)
    $events=Correlated $owner $main
    Check 'Audit Center accesses are themselves audited' (
        @($events | Where-Object { $_.action -eq 'AUDIT_CENTER_READ' -and $_.actor -eq 'logavasu' }).Count -eq 1)

    $alpha=($data.companies | Where-Object name -eq 'Isolation Alpha').id
    $bravo=($data.companies | Where-Object name -eq 'Isolation Bravo').id
    $marker='AUDIT-PRIVATE-' + [guid]::NewGuid().ToString('N')
    $open=Request Put '/platform/workspace' 200 $owner @{companyId=$alpha;reason=$marker}
    $workspace=(Json $open).id
    $events=Correlated $owner $open
    Check 'Admin View entry records the selected company and grant' (
        @($events | Where-Object { $_.action -eq 'WORKSPACE_OPEN' -and $_.tenant_id -eq $alpha -and $_.workspace -eq $workspace }).Count -eq 1)
    Check 'Stored free-form reasons and raw paths are excluded' (
        (ConvertTo-Json $events -Depth 10) -notmatch "$marker|request_path|remote_address|passwordHash|bankAccountNo|grossPay")
    $null=Request Delete "/platform/workspace/$workspace" 204 $owner

    $financial=(Json (Request Get "/platform/audit?companyId=$alpha&source=FINANCE&search=CHANGE_EXECUTED" 200 $owner)).events
    Check 'Finance filter returns only the selected company' (
        $financial.Count -gt 0 -and @($financial | Where-Object tenant_id -ne $alpha).Count -eq 0)
    $approval=$financial[0].approval_id
    $history=(Json (Request Get "/platform/audit?source=FINANCE&approvalId=$approval" 200 $owner)).events
    Check 'Approval history tracks request, distinct approval and execution' (
        'CHANGE_QUEUED' -in $history.action -and 'CHANGE_APPROVED' -in $history.action -and
        'CHANGE_EXECUTED' -in $history.action -and $history[0].maker -ne $history[0].checker -and
        $history[0].approval_state -eq 'EXECUTED')
    Check 'Approval history omits request bodies, fingerprints and financial values' (
        (ConvertTo-Json $history -Depth 10) -notmatch 'fingerprint|bankAccountNo|grossPay|LOCAL-CONTROL-|password')
    $foreign=(Json (Request Get "/platform/audit?companyId=$bravo&approvalId=$approval" 200 $owner)).events
    Check 'Approval from Alpha is excluded by the Bravo company filter' ($foreign.Count -eq 0)
    $rejected=(Json (Request Get '/platform/audit?source=FINANCE&search=CHANGE_REJECTED' 200 $owner)).events
    Check 'Rejected approvals are distinguishable from executed changes' (
        @($rejected | Where-Object approval_state -eq 'REJECTED').Count -gt 0)
    Write-Host "$passed Audit Center API checks passed."
} finally {
    foreach ($entry in $sessions.GetEnumerator()) {
        $path=if ($entry.Value) {'/auth/logout'} else {'/platform/logout'}
        $null=Invoke-WebRequest -Method Post -Uri "$base$path" -Headers @{Authorization="Bearer $($entry.Key)"} -SkipHttpErrorCheck
    }
}
