# Run only against the Admin View isolation backend and its synthetic companies.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$ownerPassword = $env:HRMS_ISOLATION_OWNER_PASSWORD
$fixturePassword = $env:HRMS_ISOLATION_FIXTURE_PASSWORD
if (-not $ownerPassword -or -not $fixturePassword) { throw 'Set the local owner and fixture passwords.' }
$marker = 'Admin View verification ' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$passed = 0
$owner = $null
$second = $null
$employee = $null

function Request($Method, $Path, $Token, $Body, $Grant, $Headers = @{}) {
    $options = @{ Uri = "$base$Path"; Method = $Method; SkipHttpErrorCheck = $true; TimeoutSec = 10; Headers = $Headers.Clone() }
    if ($Token) { $options.Headers.Authorization = "Bearer $Token" }
    if ($Grant) { $options.Headers['X-HRMS-Workspace'] = [string]$Grant }
    if ($null -ne $Body) {
        $options.ContentType = 'application/json'
        $options.Body = ConvertTo-Json -InputObject $Body -Depth 6 -Compress
    }
    Invoke-WebRequest @options
}

function Check($Name, [bool]$Condition) {
    if (-not $Condition) { throw "$Name failed." }
    $script:passed++
    Write-Host "PASS $Name"
}

function Status($Name, $Result, [int[]]$Expected) {
    Check "$Name (HTTP $($Result.StatusCode))" ($Result.StatusCode -in $Expected)
}

function Payload($Result) {
    if (-not $Result.Content) { return $null }
    $body = $Result.Content
    if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
    ConvertFrom-Json -InputObject $body -NoEnumerate
}

try {
    $credentials = @{ username = 'logavasu'; password = $ownerPassword }
    $login = Request Post '/platform/auth/login' $null $credentials
    Status 'owner signs in once' $login 200
    $owner = (Payload $login).token
    $session = Payload (Request Get '/platform/session' $owner)
    Check 'platform identity and staging feature are preserved' ($session.username -eq 'logavasu' -and $session.adminView)
    $companyList = (Payload (Request Get '/platform/companies?search=isolation-' $owner)).content
    $a = $companyList | Where-Object code -eq 'isolation-alpha'
    $b = $companyList | Where-Object code -eq 'isolation-bravo'
    Check 'Alpha and Bravo exist in the registry' ([bool]($a.id -and $b.id))
    $pendingCompany = (Payload (Request Get '/platform/companies?status=PENDING' $owner)).content | Select-Object -First 1
    Check 'pending fixture exists for read-only verification' ([bool]$pendingCompany.id)
    Status 'owner token alone cannot call tenant API' (Request Get '/core/employees' $owner) 401
    Status 'access reason is required' (Request Put '/platform/workspace' $owner @{
        companyId = $a.id; reason = '   '
    }) 400
    Status 'unknown company cannot be selected' (Request Put '/platform/workspace' $owner @{
        companyId = [Guid]::NewGuid(); reason = $marker
    }) 404
    $alphaResponse = Request Put '/platform/workspace' $owner @{ companyId = $a.id; reason = "$marker Alpha" }
    Status 'open Alpha' $alphaResponse 200
    $alpha = Payload $alphaResponse
    Check 'opening binds Alpha and returns an audit correlation ID' (
        $alpha.companyId -eq $a.id -and [bool]$alphaResponse.Headers['X-Request-Id'])
    $alphaRows = Request Get '/core/employees?page=0&size=20' $owner $null $alpha.id
    Status 'owner Alpha employee directory' $alphaRows 200
    Check 'Alpha rows only and no response caching' (
        (Payload $alphaRows).content.Count -eq 2 -and
        @((Payload $alphaRows).content | Where-Object employeeCode -notlike 'VERIFY-ALPHA*').Count -eq 0 -and
        (($alphaRows.Headers['Cache-Control'] -join ',') -match 'no-store'))
    $alphaEmployee = (Payload $alphaRows).content | Where-Object employeeCode -eq 'VERIFY-ALPHA'
    $spoofed = Request Get "/core/employees?page=0&tenantId=$($b.id)" $owner $null $alpha.id @{ 'X-Company-Id' = $b.id }
    Status 'client company hints cannot replace the grant' $spoofed 200
    Check 'spoofed request still returns Alpha' (
        @((Payload $spoofed).content | Where-Object employeeCode -notlike 'VERIFY-ALPHA*').Count -eq 0)
    Status 'owner can inspect the company dashboard' (Request Get '/dashboard/summary' $owner $null $alpha.id) 200
    Status 'Alpha payroll requires financial step-up' (Request Get '/payroll/runs' $owner $null $alpha.id) 428
    $alphaVerification = Request Post '/sensitive/step-up' $owner @{password=$ownerPassword} $alpha.id
    Check 'owner verifies the selected Alpha grant' ((Payload $alphaVerification).verified)
    Status 'verified owner can inspect Alpha payroll' (Request Get '/payroll/runs' $owner $null $alpha.id) 200
    Status 'fresh owner can inspect an authorized bank record' (
        Request Get "/core/employees/$($alphaEmployee.id)/bank-details" $owner $null $alpha.id) 200
    Status 'owner is denied employee self-service' (Request Get '/me/profile' $owner $null $alpha.id) 403

    $companyLogin = Request Post '/auth/login' $null @{
        companyCode = 'isolation-alpha'; username = 'isolation.alpha.hr'; password = $fixturePassword
    }
    Status 'ordinary company login still works' $companyLogin 200
    $employee = (Payload $companyLogin).token
    Status 'company principal cannot use owner grant' (Request Get '/core/employees' $employee $null $alpha.id) 401
    Status 'company principal cannot select a workspace' (Request Put '/platform/workspace' $employee @{
        companyId = $a.id; reason = $marker
    }) @(401, 403)
    $secondLogin = Request Post '/platform/auth/login' $null $credentials
    Status 'second independent owner session signs in for the binding test' $secondLogin 200
    $second = (Payload $secondLogin).token
    Status 'another owner session cannot replay Alpha grant' (Request Get '/core/employees' $second $null $alpha.id) 410

    $write = Request Post '/core/branches' $owner @{ name = "$marker Branch"; description = 'Synthetic Admin View test' } $alpha.id
    Status 'owner creates a synthetic Alpha branch' $write 200
    $branch = Payload $write
    Check 'tenant record createdBy is the real platform owner' ($branch.createdBy -eq 'logavasu')
    $bravoResponse = Request Put '/platform/workspace' $owner @{
        companyId = $b.id; expectedWorkspaceId = $alpha.id; reason = "$marker Bravo"
    }
    Status 'switch Alpha to Bravo with the original owner token' $bravoResponse 200
    $bravo = Payload $bravoResponse
    Check 'switch issues a different Bravo grant' ($bravo.companyId -eq $b.id -and $bravo.id -ne $alpha.id)
    Status 'old Alpha grant cannot be replayed' (Request Get '/core/employees' $owner $null $alpha.id) 410
    Status 'stale switch cannot replace Bravo' (Request Put '/platform/workspace' $owner @{
        companyId = $a.id; expectedWorkspaceId = $alpha.id; reason = "$marker Stale"
    }) 410
    Status 'stale close cannot close Bravo' (Request Delete "/platform/workspace/$($alpha.id)" $owner) 410
    $bravoRows = Request Get '/core/employees?page=0&size=20' $owner $null $bravo.id
    Status 'owner Bravo directory' $bravoRows 200
    Check 'Bravo rows only' (
        (Payload $bravoRows).content.Count -eq 2 -and
        @((Payload $bravoRows).content | Where-Object employeeCode -notlike 'VERIFY-BRAVO*').Count -eq 0)
    $bravoEmployee = (Payload $bravoRows).content | Where-Object employeeCode -eq 'VERIFY-BRAVO'
    Status 'Bravo cannot read an Alpha employee by ID' (Request Get "/core/employees/$($alphaEmployee.id)" $owner $null $bravo.id) 404
    $bravoVerification = Request Post '/sensitive/step-up' $owner @{password=$ownerPassword} $bravo.id
    Check 'owner verifies the selected Bravo grant' ((Payload $bravoVerification).verified)
    Status 'Bravo cannot read an Alpha employee bank record by ID' (
        Request Get "/core/employees/$($alphaEmployee.id)/bank-details" $owner $null $bravo.id) 404
    Status 'Bravo cannot see Alpha branch written by owner' (Request Get "/core/branches/$($branch.id)" $owner $null $bravo.id) 404
    Check 'the selected Bravo workspace is returned after a reload' (
        (Payload (Request Get '/platform/workspace' $owner)).id -eq $bravo.id)
    $alphaAgain = Request Put '/platform/workspace' $owner @{
        companyId = $a.id; expectedWorkspaceId = $bravo.id; reason = "$marker Alpha again"
    }
    Status 'switch Bravo to Alpha with the original owner token' $alphaAgain 200
    $reopened = Payload $alphaAgain
    Status 'Alpha cannot read a Bravo employee by ID' (Request Get "/core/employees/$($bravoEmployee.id)" $owner $null $reopened.id) 404
    Status 'Bravo grant is revoked in the reverse switch' (Request Get '/core/employees' $owner $null $bravo.id) 410

    $pending = Request Put '/platform/workspace' $owner @{
        companyId = $pendingCompany.id; expectedWorkspaceId = $reopened.id; reason = "$marker Pending"
    }
    Status 'enter a pending company for inspection' $pending 200
    $readOnly = Payload $pending
    Status 'pending company read remains available' (Request Get '/core/employees' $owner $null $readOnly.id) 200
    Status 'pending company operation is denied' (
        Request Post '/core/branches' $owner @{ name = "$marker Denied" } $readOnly.id) 403
    Status 'close the active grant on return to Control Tower' (Request Delete "/platform/workspace/$($readOnly.id)" $owner) 204
    Status 'closed grant cannot be replayed' (Request Get '/core/employees' $owner $null $readOnly.id) 410
    Check 'platform session survives closing the workspace' (
        (Payload (Request Get '/platform/session' $owner)).username -eq 'logavasu' -and
        $null -eq (Payload (Request Get '/platform/workspace' $owner)))
    foreach ($action in @('WORKSPACE_OPEN', 'WORKSPACE_SWITCH', 'WORKSPACE_REQUEST', 'WORKSPACE_REQUEST_DENIED', 'WORKSPACE_CLOSE')) {
        $events = (Payload (Request Get "/platform/activity?action=$action" $owner)).events.content
        Check "$action is visible with owner attribution and a correlated request" (
            @($events | Where-Object { $_.actor -eq 'logavasu' -and $_.request_id -and $_.http_status }).Count -gt 0)
    }
} finally {
    if ($employee) { Request Post '/auth/logout' $employee | Out-Null }
    if ($second) { Request Post '/platform/logout' $second | Out-Null }
    if ($owner) { Request Post '/platform/logout' $owner | Out-Null }
}
Write-Host "$passed Admin View API checks passed."
