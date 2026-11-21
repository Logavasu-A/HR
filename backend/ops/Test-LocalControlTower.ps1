$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$ownerPassword = $env:HRMS_ISOLATION_OWNER_PASSWORD
$fixturePassword = $env:HRMS_ISOLATION_FIXTURE_PASSWORD
if (-not $ownerPassword -or -not $fixturePassword) { throw 'Set the local owner and fixture passwords.' }
$owner = $null
$tenant = $null
$checked = 0

function Request($Method, $Path, $Token, $Body) {
    $options = @{ Uri = "$base$Path"; Method = $Method; SkipHttpErrorCheck = $true; TimeoutSec = 15 }
    if ($Token) { $options.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $options.ContentType = 'application/json'
        $options.Body = ConvertTo-Json -InputObject $Body -Compress
    }
    Invoke-WebRequest @options
}
function Response-Json($Result) {
    $body = $Result.Content
    if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
    ConvertFrom-Json -InputObject $body
}
function Check($Name, [bool]$Condition) {
    if (-not $Condition) { throw "$Name failed." }
    $script:checked++
    Write-Host "PASS $Name"
}

try {
    Check 'the report requires authentication' ((Request Get '/platform/control-tower').StatusCode -eq 401)
    $auth = Request Post '/platform/auth/login' $null @{ username = 'logavasu'; password = $ownerPassword }
    Check 'platform sign-in' ($auth.StatusCode -eq 200)
    $owner = (Response-Json $auth).token
    $standard = Request Post '/auth/login' $null @{
        companyCode = 'isolation-alpha'; username = 'isolation.alpha.hr'; password = $fixturePassword
    }
    Check 'company sign-in' ($standard.StatusCode -eq 200)
    $tenant = (Response-Json $standard).token
    Check 'company JWT cannot use global metrics' ((Request Get '/platform/control-tower' $tenant).StatusCode -in @(401,403))

    $year = (Get-Date).Year
    $response = Request Get "/platform/control-tower?year=$year" $owner
    Check 'owner report has no-cache and correlation headers' (
        $response.StatusCode -eq 200 -and (($response.Headers['Cache-Control'] -join ',') -match 'no-store') -and
        [bool]$response.Headers['X-Request-Id'])
    $global = Response-Json $response
    Check 'annual report includes 12 periods and the company registry' (
        $global.months.Count -eq 12 -and $global.companies.Count -ge 2)
    $alpha = $global.companies | Where-Object code -eq 'isolation-alpha'
    $bravo = $global.companies | Where-Object code -eq 'isolation-bravo'
    Check 'known Alpha and Bravo employee counts match the local fixtures' (
        $alpha.employees -eq 2 -and $bravo.employees -eq 2 -and $alpha.activeEmployees -eq 2 -and $bravo.activeEmployees -eq 2)
    Check 'aggregate payload excludes individual and confidential fields' (
        ($global | ConvertTo-Json -Depth 20) -notmatch '"(firstName|lastName|bankAccountNo|email|passwordHash|grossPay|employeeId|secretHash|message)"')
    foreach ($row in @($alpha,$bravo)) {
        $result = Request Get "/platform/control-tower?year=$year&companyId=$($row.id)" $owner
        Check "company drill-down for $($row.code) succeeds" ($result.StatusCode -eq 200)
        $drill = Response-Json $result
        Check "company drill-down for $($row.code) matches only its own rollup" (
            $drill.companies.Count -eq 1 -and
            (ConvertTo-Json -InputObject $drill.companies[0] -Depth 12 -Compress) -ceq
            (ConvertTo-Json -InputObject $row -Depth 12 -Compress))
    }
    $monthly = Request Get "/platform/control-tower?year=$year&month=11&companyId=$($alpha.id)" $owner
    Check 'monthly selection returns just November' (
        $monthly.StatusCode -eq 200 -and (Response-Json $monthly).months.Count -eq 1 -and
        (Response-Json $monthly).months[0].month -eq "$year-11")
    Check 'out-of-range month is rejected' ((Request Get "/platform/control-tower?year=$year&month=13" $owner).StatusCode -eq 400)
    Check 'missing company returns 404' (
        (Request Get "/platform/control-tower?companyId=$([Guid]::NewGuid())" $owner).StatusCode -eq 404)
    $events = Response-Json (Request Get '/platform/activity?action=CONTROL_TOWER_READ' $owner)
    Check 'analytics reads are attributable and correlated' (
        @($events.events.content | Where-Object { $_.actor -eq 'logavasu' -and $_.request_id -and $_.http_status -eq '200' }).Count -gt 0)
} finally {
    if ($owner) { Request Post '/platform/logout' $owner | Out-Null }
    if ($tenant) { Request Post '/auth/logout' $tenant | Out-Null }
}
Write-Host "$checked Control Tower API checks passed."
