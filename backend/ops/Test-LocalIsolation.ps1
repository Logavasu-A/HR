# Run against the local-isolation backend after loading local-isolation-fixtures.sql.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$fixturePassword = $env:HRMS_ISOLATION_FIXTURE_PASSWORD
if ([string]::IsNullOrEmpty($fixturePassword)) {
    throw 'Set HRMS_ISOLATION_FIXTURE_PASSWORD for the synthetic accounts.'
}

$sessions = @{}
$passed = 0

function Request($Method, $Path, $Token, $Body) {
    $request = @{
        Uri = "$base$Path"; Method = $Method; SkipHttpErrorCheck = $true; TimeoutSec = 10
    }
    if ($Token) { $request.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 8 -Compress
    }
    Invoke-WebRequest @request
}

function Expect($Label, $Response, [int[]]$Status) {
    if ($Response.StatusCode -notin $Status) {
        throw "$Label returned HTTP $($Response.StatusCode), expected $($Status -join ' or ')."
    }
    $script:passed++
    Write-Host "PASS $Label"
}

function Assert-Fact($Label, [bool]$Condition) {
    if (-not $Condition) { throw "$Label failed." }
    $script:passed++
    Write-Host "PASS $Label"
}

function Json-Body($Response) {
    $body = $Response.Content
    if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
    ConvertFrom-Json -InputObject $body -NoEnumerate
}

function Assert-NoBankFields($Label, $Response) {
    $body = $Response.Content
    if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
    Assert-Fact $Label ($body -notmatch '"(bankName|bankAccountNo)"s*:')
}

try {
    $tenants = @('alpha', 'bravo')
    $snapshot = @{}
    foreach ($tenant in $tenants) {
        foreach ($kind in @('hr', 'reader', 'bank')) {
            $login = Request Post '/auth/login' $null @{
                companyCode = "isolation-$tenant"
                username = "isolation.$tenant.$kind"
                password = $fixturePassword
            }
            Expect "$tenant $kind sign-in" $login 200
            $sessions["$tenant.$kind"] = (Json-Body $login).token
        }
        $token = $sessions["$tenant.hr"]
        $directory = Request Get '/core/employees?page=0&size=20' $token
        Expect "$tenant employee directory" $directory 200
        Assert-NoBankFields "$tenant directory omits banking fields" $directory
        $rows = (Json-Body $directory).content
        $code = 'VERIFY-' + $tenant.ToUpperInvariant()
        $employee = $rows | Where-Object employeeCode -eq $code
        Assert-Fact "$tenant directory contains only its own records" (
            $rows.Count -eq 2 -and $employee.id -and
            @($rows | Where-Object { $_.employeeCode -notlike "$code*" }).Count -eq 0)
        $runs = Request Get '/payroll/runs?page=0&size=20' $token
        Expect "$tenant payroll runs" $runs 200
        $runRows = (Json-Body $runs).content
        Assert-Fact "$tenant payroll run count" ($runRows.Count -eq 1)
        $snapshot[$tenant] = @{ Employee = $employee; RunId = $runRows[0].id; Code = $code }
    }

    foreach ($tenant in $tenants) {
        $other = $tenants | Where-Object { $_ -ne $tenant }
        $local = $snapshot[$tenant]
        $foreign = $snapshot[$other]
        $token = $sessions["$tenant.hr"]
        $reader = $sessions["$tenant.reader"]
        $editor = $sessions["$tenant.bank"]
        $localPath = "/core/employees/$($local.Employee.id)"
        $foreignPath = "/core/employees/$($foreign.Employee.id)"

        $direct = Request Get $localPath $token
        Expect "$tenant own employee detail" $direct 200
        Assert-NoBankFields "$tenant employee detail omits banking fields" $direct
        Expect "$tenant cannot read $other employee" (Request Get $foreignPath $token) 404
        Expect "$tenant cannot update $other employee" (Request Put $foreignPath $token @{
            firstName = 'Blocked'; dateOfJoining = '2025-02-03'; status = 'ACTIVE'
        }) 404
        Expect "$tenant cannot delete $other employee" (Request Delete $foreignPath $token) 404
        Expect "$tenant cannot create an employee in a $other department" (Request Post '/core/employees' $token @{
            employeeCode = 'BLOCKED-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
            firstName = 'Blocked'; dateOfJoining = '2025-02-03'; status = 'ACTIVE'
            department = @{ id = $foreign.Employee.department.id }
        }) @(400, 404, 409)
        Expect "$tenant cannot move an employee to a $other department" (Request Put $localPath $token @{
            employeeCode = $local.Code; firstName = $local.Employee.firstName; lastName = $local.Employee.lastName
            dateOfJoining = $local.Employee.dateOfJoining; status = 'ACTIVE'
            department = @{ id = $foreign.Employee.department.id }
        }) @(400, 404, 409)
        Expect "$tenant cannot change an employee code to a same-company duplicate" (Request Put $localPath $token @{
            employeeCode = "$($local.Code)-COLLEAGUE"; firstName = $local.Employee.firstName
            dateOfJoining = $local.Employee.dateOfJoining; status = 'ACTIVE'
            department = @{ id = $local.Employee.department.id }
        }) 409
        Expect "$tenant company token cannot open the control tower" (Request Get '/platform/dashboard' $token) @(401, 403)

        $report = Request Get '/attendance/summary' $token
        Expect "$tenant attendance report" $report 200
        $attendanceEmployees = (Json-Body $report).employees
        Assert-Fact "$tenant attendance report contains no foreign employee" (
            $attendanceEmployees.Count -eq 2 -and
            @($attendanceEmployees | Where-Object { $_.employeeCode -notlike "$($local.Code)*" }).Count -eq 0)
        $leave = Request Get '/leave/applications?page=0&size=20' $token
        Expect "$tenant leave listing" $leave 200
        Assert-Fact "$tenant leave listing is scoped" (
            @((Json-Body $leave).content).Count -eq 1 -and
            (Json-Body $leave).content[0].employee.employeeCode -eq "$($local.Code)-COLLEAGUE")
        $payroll = Request Get "/payroll/runs/$($local.RunId)/slips?page=0&size=20" $token
        Expect "$tenant payroll slips" $payroll 200
        Assert-NoBankFields "$tenant payroll cannot disclose nested banking fields" $payroll
        Assert-Fact "$tenant payroll slips are scoped" (
            @((Json-Body $payroll).content).Count -eq 2 -and
            @((Json-Body $payroll).content | Where-Object { $_.employee.employeeCode -notlike "$($local.Code)*" }).Count -eq 0)
        $foreignPayroll = Request Get "/payroll/runs/$($foreign.RunId)/slips?page=0&size=20" $token
        Expect "$tenant cross-company payroll query returns a scoped response" $foreignPayroll 200
        Assert-Fact "$tenant cross-company payroll response contains no slips" ((Json-Body $foreignPayroll).content.Count -eq 0)
        $self = Request Get '/me/payslips?page=0&size=20' $token
        Expect "$tenant employee self-service payslips" $self 200
        Assert-Fact "$tenant employee cannot see the colleague's payslip in self-service" (
            (Json-Body $self).content.Count -eq 1 -and (Json-Body $self).content[0].employee.id -eq $local.Employee.id)
        $notifications = Request Get '/notifications?page=0&size=20' $token
        Expect "$tenant notifications" $notifications 200
        Assert-Fact "$tenant notifications are scoped" (
            (Json-Body $notifications).items.Count -eq 1 -and
            (Json-Body $notifications).items[0].title -like "$tenant*")

        $bankPath = "$localPath/bank-details"
        $sample = @{ bankName = "$tenant Fixture Bank"; bankAccountNo = "VERIFICATION-$($tenant.ToUpperInvariant())" }
        Expect "$tenant wildcard role cannot read banking fields" (Request Get $bankPath $token) 403
        Expect "$tenant wildcard role cannot write banking fields" (Request Put $bankPath $token $sample) 403
        $bankRead = Request Get $bankPath $reader
        Expect "$tenant explicit bank reader can read" $bankRead 200
        Assert-Fact "$tenant banking response disables caching" (($bankRead.Headers['Cache-Control'] -join ',') -match 'no-store')
        Expect "$tenant bank reader cannot write" (Request Put $bankPath $reader $sample) 403
        Expect "$tenant bank editor cannot read $other banking fields" (Request Get "$foreignPath/bank-details" $editor) 404
        Expect "$tenant bank editor cannot write $other banking fields" (Request Put "$foreignPath/bank-details" $editor $sample) 404
        Expect "$tenant bank editor can update its own company" (Request Put $bankPath $editor $sample) 204
        $readback = Request Get $bankPath $reader
        Expect "$tenant banking readback" $readback 200
        Assert-Fact "$tenant bank details were updated" ((Json-Body $readback).bankAccountNo -ceq $sample.bankAccountNo)

        $ordinaryEdit = @{
            employeeCode = $local.Code; firstName = $local.Employee.firstName; lastName = $local.Employee.lastName
            dateOfJoining = $local.Employee.dateOfJoining; status = 'ACTIVE'
            department = @{ id = $local.Employee.department.id }
            bankName = 'Blocked ordinary update'; bankAccountNo = 'BLOCKED-ORDINARY-API'
        }
        Expect "$tenant ordinary employee update succeeds" (Request Put $localPath $token $ordinaryEdit) 200
        $afterEdit = Request Get $bankPath $reader
        Assert-Fact "$tenant ordinary update did not overwrite protected banking fields" (
            (Json-Body $afterEdit).bankAccountNo -ceq $sample.bankAccountNo)
        $roles = Json-Body (Request Get '/admin/roles?page=0&size=20' $token)
        $protectedRole = $roles.content | Where-Object name -eq 'Isolation Bank Editor'
        Assert-Fact "$tenant protected role exists" ($null -ne $protectedRole)
        Expect "$tenant wildcard admin cannot assign a protected banking role" (Request Post '/admin/users' $token @{
            username = 'blocked-' + [Guid]::NewGuid().ToString('N')
            password = $fixturePassword
            role = @{ id = $protectedRole.id }
        }) 403
        Expect "$tenant wildcard admin cannot create a banking grant" (Request Post '/admin/roles' $token @{
            name = 'Blocked ' + [Guid]::NewGuid().ToString('N'); permissions = @('employee-bank:read')
        }) 403
        $userRows = Request Get '/admin/users?page=0&size=20' $token
        Expect "$tenant admin user listing" $userRows 200
        Assert-NoBankFields "$tenant user list cannot disclose nested banking fields" $userRows
        $protectedUser = (Json-Body $userRows).content | Where-Object username -eq "isolation.$tenant.bank"
        Expect "$tenant wildcard admin cannot reset a banking account" (
            Request Put "/admin/users/$($protectedUser.id)" $token @{
                username = $protectedUser.username; password = $fixturePassword; active = $true
                role = @{ id = $protectedRole.id }
            }) 403
        $audit = Request Get '/admin/audit-logs' $token
        Expect "$tenant audit trail" $audit 200
        $auditRows = Json-Body $audit
        Assert-Fact "$tenant banking read and write are audited without the bank account value" (
            @($auditRows | Where-Object action -eq 'READ_BANK_DETAILS').Count -gt 0 -and
            @($auditRows | Where-Object action -eq 'UPDATE_BANK_DETAILS').Count -gt 0 -and
            "$($audit.Content)" -notlike "*$($sample.bankAccountNo)*")
        Assert-Fact "$tenant rejected write to a foreign employee remains auditable" (
            @($auditRows | Where-Object { $_.method -eq 'PUT' -and $_.status -eq 404 -and
                $_.path -eq "/api$foreignPath" }).Count -gt 0)
    }
} finally {
    foreach ($token in $sessions.Values) {
        if ($token) { Request Post '/auth/logout' $token | Out-Null }
    }
}

Write-Host "$passed isolation and banking checks passed."
