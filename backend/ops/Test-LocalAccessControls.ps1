# Requires the local access verification fixtures and a running isolation backend.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$fixturePassword = $env:HRMS_ISOLATION_FIXTURE_PASSWORD
if (-not $fixturePassword) { throw 'Set HRMS_ISOLATION_FIXTURE_PASSWORD.' }
$passed = 0
$sessions = @{}

function Request($Method, $Path, [int]$Status, $Token, $Body, $WorkspaceGrant) {
    $options = @{ Method=$Method; Uri="$base$Path"; SkipHttpErrorCheck=$true; TimeoutSec=20 }
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($WorkspaceGrant) { $headers['X-HRMS-Workspace'] = [string]$WorkspaceGrant }
    $options.Headers = $headers
    if ($null -ne $Body) {
        $options.ContentType = 'application/json'
        $options.Body = ConvertTo-Json $Body -Depth 6 -Compress
    }
    $response = Invoke-WebRequest @options
    if ($response.StatusCode -ne $Status) { throw "$Method $Path returned $($response.StatusCode), expected $Status." }
    $script:passed++
    if ($response.Content -and $response.Headers['Content-Type'] -match 'json') {
        return ConvertFrom-Json -InputObject $response.Content -NoEnumerate
    }
}

function Check($Label, [bool]$Fact) {
    if (-not $Fact) { throw $Label }
    $script:passed++
    Write-Host "PASS $Label"
}

function SignInCompany($Account, $Company = 'isolation-access', $Username = "isolation.access.$Account") {
    $token = (Request Post '/auth/login' 200 $null @{
        companyCode=$Company; username=$Username; password=$fixturePassword
    }).token
    $sessions[$Account] = $token
    return $token
}

function SignInPlatform($Account, $Status = 200) {
    $token = (Request Post '/platform/auth/login' $Status $null @{
        username="isolation-$Account"; password=$fixturePassword
    }).token
    if ($token) { $sessions[$Account] = $token }
    return $token
}

try {
    $admin = SignInCompany admin
    $hr = SignInCompany hr
    $dept = SignInCompany dept
    $report = SignInCompany report
    $employee = SignInCompany employee
    $unlinked = SignInCompany unlinked
    $bravo = SignInCompany bravo 'isolation-bravo' 'isolation.bravo.hr'
    $platform = SignInPlatform 'platform-admin'
    $security = SignInPlatform 'security-admin'

    $directory = '/core/employees?page=0&size=20'
    $rows = (Request Get $directory 200 $admin).content
    $ids = @{}
    foreach ($row in $rows) { $ids[($row.employeeCode -replace '^VERIFY-ACCESS-','')] = $row.id }
    Check 'Company Administrator sees all five company employees' ($ids.Count -eq 5)
    Check 'HR Administrator sees company employees' ((Request Get $directory 200 $hr).totalElements -eq 5)
    $foreign = (Request Get $directory 200 $bravo).content[0].id
    Check 'Department Manager session advertises department scope' ((Request Get '/auth/me' 200 $dept).recordScope -eq 'DEPARTMENT')
    Check 'Reporting Manager session advertises direct-report scope' ((Request Get '/auth/me' 200 $report).recordScope -eq 'DIRECT_REPORTS')

    $deptRows = (Request Get $directory 200 $dept).content
    $reportRows = (Request Get $directory 200 $report).content
    Check 'Department Manager sees only their department' (
        ($deptRows.employeeCode | Sort-Object) -join ',' -eq 'VERIFY-ACCESS-DEPT,VERIFY-ACCESS-DIRECT,VERIFY-ACCESS-PEER')
    Check 'Reporting Manager sees self and direct report across departments' (
        ($reportRows.employeeCode | Sort-Object) -join ',' -eq 'VERIFY-ACCESS-DIRECT,VERIFY-ACCESS-REPORT')
    Check 'Unlinked manager receives no employee rows' ((Request Get $directory 200 $unlinked).totalElements -eq 0)
    Check 'Department Manager cannot read the reporting manager through a nested employee relationship' (
        -not ($deptRows | ConvertTo-Json -Depth 12 -Compress).Contains('VERIFY-ACCESS-REPORT'))
    $deptAttendance = (Request Get '/attendance/records?page=0&size=20' 200 $dept).content
    $reportAttendance = (Request Get '/attendance/records?page=0&size=20' 200 $report).content
    Check 'Department Manager attendance follows department scope' (
        (($deptAttendance.employee.employeeCode | Sort-Object -Unique) -join ',') -eq
        (($deptRows.employeeCode | Sort-Object) -join ','))
    Check 'Reporting Manager attendance follows direct reports' (
        (($reportAttendance.employee.employeeCode | Sort-Object -Unique) -join ',') -eq
        (($reportRows.employeeCode | Sort-Object) -join ','))

    $null = Request Get "/core/employees/$($ids.DIRECT)" 200 $report
    $null = Request Get "/core/employees/$($ids.INDIRECT)" 404 $report
    $null = Request Get "/core/employees/$($ids.REPORT)" 404 $dept
    $null = Request Get "/core/employees/$foreign" 404 $dept
    $null = Request Get "/core/employees/$foreign" 404 $report
    $null = Request Get "/leave/balances?employeeId=$foreign" 404 $report
    $null = Request Get "/core/employees/$($ids.INDIRECT)" 404 $bravo
    $null = Request Get "/core/employees/$($ids.PEER)" 403 $employee
    $null = Request Delete "/core/employees/$($ids.DIRECT)" 403 $report
    $null = Request Get "/core/employees/$($ids.DIRECT)/bank-details" 403 $report
    $null = Request Get '/payroll/runs' 403 $dept
    $null = Request Get '/admin/users' 403 $dept
    $null = Request Post '/admin/roles' 403 $hr @{name='Privilege Escalation Probe';permissions=@('*')}
    $null = Request Post '/admin/roles' 400 $admin @{
        name='Conflicting Scope Probe';permissions=@('core:read','scope:department','scope:direct-reports')
    }
    Check 'Employee self service still returns their own profile' (
        (Request Get '/me/profile' 200 $employee).employeeCode -eq 'VERIFY-ACCESS-DIRECT')
    Check 'Employee verifies before viewing published payslips' (
        (Request Post '/sensitive/step-up' 200 $employee @{password=$fixturePassword}).verified)
    $slips = (Request Get '/me/payslips?page=0&size=20' 200 $employee).content
    Check 'Employee sees one payslip and no company payroll run totals' (
        $slips.Count -eq 1 -and $slips[0].employee.employeeCode -eq 'VERIFY-ACCESS-DIRECT' -and $null -eq $slips[0].run)

    $applications = '/leave/applications'
    $allLeave = (Request Get "$($applications)?page=0&size=20" 200 $admin).content
    $leave = @{}
    foreach ($entry in $allLeave) { $leave[($entry.reason -replace '^VERIFY-ACCESS-','')] = $entry }
    $null = Request Post "$applications/$($leave.INDIRECT.id)/approve" 404 $report @{}
    $null = Request Post "$applications/$($leave.DEPT.id)/approve" 403 $dept @{}
    $null = Request Post "$applications/$($leave.REPORT.id)/approve" 404 $dept @{}
    if ($leave.DIRECT.status -eq 'PENDING') {
        Check 'Reporting Manager can approve a direct report leave' (
            (Request Post "$applications/$($leave.DIRECT.id)/approve" 200 $report @{remarks='Local scope verification'}).status -eq 'APPROVED')
    } else {
        Check 'Reporting Manager can view their previously approved direct report leave' (
            (Request Get "$applications/$($leave.DIRECT.id)" 200 $report).status -eq 'APPROVED')
    }
    if ($leave.PEER.status -eq 'PENDING') {
        Check 'Department Manager can reject leave in their department' (
            (Request Post "$applications/$($leave.PEER.id)/reject" 200 $dept @{remarks='Local scope verification'}).status -eq 'REJECTED')
    } else {
        Check 'Department Manager can view their previously rejected department leave' (
            (Request Get "$applications/$($leave.PEER.id)" 200 $dept).status -eq 'REJECTED')
    }

    $null = Request Get '/platform/companies' 401 $admin
    $null = Request Get '/platform/companies' 200 $platform
    Check 'Platform Administrator registry summary omits audit events' (
        (Request Get '/platform/dashboard' 200 $platform).activity.Count -eq 0)
    $null = Request Get '/platform/control-tower' 403 $platform
    $null = Request Get '/platform/activity' 403 $platform
    $null = Request Get '/platform/access' 403 $platform
    $null = Request Get $directory 401 $platform $null ([guid]::NewGuid())
    $null = Request Get '/platform/companies' 403 $security
    $null = Request Get '/platform/control-tower' 403 $security
    $null = Request Get '/platform/activity' 200 $security
    $accounts = Request Get '/platform/access' 200 $security
    $founder = $accounts | Where-Object role -eq 'GLOBAL_SUPER_ADMIN'
    $candidate = $accounts | Where-Object username -eq 'isolation-platform-candidate'
    $securityRow = $accounts | Where-Object username -eq 'isolation-security-admin'
    $null = Request Put "/platform/access/$($founder.id)" 403 $security @{role='UNASSIGNED';reason='Local scope verification'}
    $null = Request Put "/platform/access/$($securityRow.id)" 403 $security @{role='PLATFORM_ADMINISTRATOR';reason='Local scope verification'}
    $null = Request Put "/platform/access/$($candidate.id)" 403 $security @{role='SECURITY_ADMINISTRATOR';reason='Local scope verification'}
    $null = SignInPlatform 'platform-candidate' 401
    $null = Request Put "/platform/access/$($candidate.id)" 200 $security @{role='PLATFORM_ADMINISTRATOR';reason='Local grant verification'}
    $candidateToken = SignInPlatform 'platform-candidate'
    $null = Request Get '/platform/companies' 200 $candidateToken
    $null = Request Put "/platform/access/$($candidate.id)" 200 $security @{role='UNASSIGNED';reason='Local revoke verification'}
    $null = Request Get '/platform/session' 401 $candidateToken
    $roleEvents = (Request Get '/platform/activity?action=PLATFORM_ROLE_CHANGE' 200 $security).events.content
    Check 'Grant and revoke carry audit actor, reason and correlation id' (
        ($roleEvents | Where-Object { $_.actor -eq 'isolation-security-admin' -and $_.reason -like '*Local grant verification*' -and $_.request_id }) -and
        ($roleEvents | Where-Object { $_.actor -eq 'isolation-security-admin' -and $_.reason -like '*Local revoke verification*' -and $_.request_id }))
    Write-Host "$passed access control checks passed."
} finally {
    foreach ($entry in $sessions.GetEnumerator()) {
        $path = if ($entry.Key -in @('platform-admin','security-admin','platform-candidate')) { '/platform/logout' } else { '/auth/logout' }
        $null = Invoke-WebRequest -Method Post -Uri "$base$path" -Headers @{Authorization="Bearer $($entry.Value)"} -SkipHttpErrorCheck
    }
}
