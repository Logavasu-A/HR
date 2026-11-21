# Use only the local staging endpoint and synthetic company accounts.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api'
$password = $env:HRMS_ISOLATION_FIXTURE_PASSWORD
$ownerPassword = $env:HRMS_ISOLATION_OWNER_PASSWORD
if (-not $password -or -not $ownerPassword) { throw 'Set the local fixture and owner passwords.' }
$passed = 0
$tokens = @{}

function Req($Method,$Path,[int]$Status,$Token,$Body,$Grant,[switch]$Confirm) {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($Grant) { $headers['X-HRMS-Workspace'] = [string]$Grant }
    if ($Confirm) { $headers['X-HRMS-Confirm']='true'; $headers['X-HRMS-Reason']='RECORD_CORRECTION' }
    $request = @{Method=$Method;Uri="$base$Path";Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=15}
    if ($null -ne $Body) {
        $request.ContentType='application/json'
        $request.Body=ConvertTo-Json $Body -Depth 8 -Compress
    }
    $response = Invoke-WebRequest @request
    if ($response.StatusCode -ne $Status) { throw "$Method $Path returned $($response.StatusCode), expected $Status." }
    $script:passed++
    if ($response.Content -and $response.Headers['Content-Type'] -match 'json') {
        return ConvertFrom-Json -InputObject $response.Content -NoEnumerate
    }
}

function Assert-Fact($Label, [bool]$Fact) {
    if (-not $Fact) { throw $Label }
    $script:passed++
    Write-Host "PASS $Label"
}

function Company($Code,$Kind,$Username="isolation.$Code.$Kind") {
    $token = (Req Post '/auth/login' 200 $null @{
        companyCode="isolation-$Code"; username=$Username; password=$password
    }).token
    $tokens[[guid]::NewGuid()] = $token
    return $token
}

function Unlock($Token,$Secret,$Grant) {
    Assert-Fact 'Correct password verifies only the current session' (
        (Req Post '/sensitive/step-up' 200 $Token @{password=$Secret} $Grant).verified)
}

function SelectCompany($Company,$OldGrant) {
    $body = @{companyId=$Company; reason='Local financial verification'}
    if ($OldGrant) { $body.expectedWorkspaceId=$OldGrant }
    return (Req Put '/platform/workspace' 200 $owner $body).id
}

try {
    $alpha = Company alpha bank
    $alphaHr = Company alpha hr
    $bravo = Company bravo bank
    $employee = Company access employee
    $owner = (Req Post '/platform/auth/login' 200 $null @{username='logavasu';password=$ownerPassword}).token
    $companies = (Req Get '/platform/companies?search=isolation-' 200 $owner).content
    $a = ($companies | Where-Object code -eq 'isolation-alpha').id
    $b = ($companies | Where-Object code -eq 'isolation-bravo').id
    $alphaId = ((Req Get '/core/employees?page=0&size=20' 200 $alpha).content | Where-Object employeeCode -eq 'VERIFY-ALPHA').id
    $bravoId = ((Req Get '/core/employees?page=0&size=20' 200 $bravo).content | Where-Object employeeCode -eq 'VERIFY-BRAVO').id
    $bankPath = "/core/employees/$alphaId/bank-details"
    Assert-Fact 'Step-up is required even after an ordinary login' ((Req Get $bankPath 428 $alpha).code -eq 'STEP_UP_REQUIRED')
    $null = Req Get $bankPath 403 $alphaHr
    $null = Req Get '/payroll/assignments' 428 $alpha
    $null = Req Get '/me/payslips?page=0&size=20' 428 $employee
    Assert-Fact 'A wrong password does not unlock the session' (-not (Req Post '/sensitive/step-up' 200 $alpha @{password='incorrect-verification-only'}).verified)
    Assert-Fact 'Company dashboard keeps payroll locked' ((Req Get '/dashboard/summary' 200 $alpha).payrollLocked)
    Unlock $alpha $password
    Unlock $bravo $password
    Unlock $employee $password
    Assert-Fact 'Company dashboard unlocks qualifying payroll totals' (-not (Req Get '/dashboard/summary' 200 $alpha).payrollLocked)
    $original = Req Get $bankPath 200 $alpha
    $null = Req Get "/core/employees/$bravoId/bank-details" 404 $alpha
    $null = Req Get $bankPath 404 $bravo
    $null = Req Get '/payroll/assignments' 200 $alpha
    $alphaRuns = (Req Get '/payroll/runs?page=0&size=20' 200 $alpha).content
    $bravoRuns = (Req Get '/payroll/runs?page=0&size=20' 200 $bravo).content
    $null = Req Get "/payroll/runs/$($alphaRuns[0].id)/slips" 200 $alpha
    Assert-Fact 'Payroll slips remain isolated in both directions' (
        (Req Get "/payroll/runs/$($bravoRuns[0].id)/slips?page=0&size=20" 200 $alpha).totalElements -eq 0 -and
        (Req Get "/payroll/runs/$($alphaRuns[0].id)/slips?page=0&size=20" 200 $bravo).totalElements -eq 0)
    Assert-Fact 'Employee can unlock only self-service payslips' (
        (Req Get '/me/payslips?page=0&size=20' 200 $employee).totalElements -eq 1)
    $null = Req Get '/payroll/runs' 403 $employee
    $second = Company alpha bank
    $null = Req Get $bankPath 428 $second

    $changed = [ordered]@{bankName=$original.bankName;bankAccountNo=('LOCAL-CONTROL-' + ([guid]::NewGuid().ToString('N').Substring(0,10)))}
    Assert-Fact 'Sensitive writes require explicit confirmation' (
        (Req Put $bankPath 428 $alpha $changed).code -eq 'CONFIRMATION_REQUIRED')
    $review = (Req Put $bankPath 202 $alpha $changed -Confirm).approvalId
    Assert-Fact 'Pending bank change leaves the stored account unchanged' (
        (Req Get $bankPath 200 $alpha).bankAccountNo -eq $original.bankAccountNo)
    $null = Req Post "/sensitive/approvals/$review/decision" 403 $alpha @{approved=$true}
    $null = Req Post "/sensitive/approvals/$review/decision" 404 $bravo @{approved=$true}
    $null = Req Put "/core/employees/$bravoId/bank-details" 404 $alpha $changed -Confirm

    $lockedTower = Req Get '/platform/control-tower' 200 $owner
    Assert-Fact 'Global payroll totals are redacted until separate owner verification' (
        $lockedTower.payrollLocked -and $lockedTower.payroll.Count -eq 0 -and
        @($lockedTower.companies | Where-Object { $null -ne $_.netPayroll }).Count -eq 0)
    Assert-Fact 'Global payroll verifies the original platform identity' (
        (Req Post '/platform/control-tower/step-up' 200 $owner @{password=$ownerPassword}).verified)
    $unlockedTower = Req Get '/platform/control-tower' 200 $owner
    Assert-Fact 'Verified owner sees database payroll rollups' (-not $unlockedTower.payrollLocked -and $unlockedTower.payroll.Count -gt 0)

    $grant = SelectCompany $a
    $null = Req Get $bankPath 428 $owner $null $grant
    Unlock $owner $ownerPassword $grant
    $queue = Req Get '/sensitive/approvals' 200 $owner $null $grant
    Assert-Fact 'Reviewer queue exposes decision metadata without financial values' (
        ($queue | Where-Object id -eq $review).canDecide -and
        -not (ConvertTo-Json $queue -Depth 6).Contains($changed.bankAccountNo))
    $null = Req Post "/sensitive/approvals/$review/decision" 204 $owner @{approved=$true} $grant
    $altered = [ordered]@{bankName='Different verification only';bankAccountNo=$changed.bankAccountNo}
    $newReview = (Req Put $bankPath 202 $alpha $altered -Confirm).approvalId
    Assert-Fact 'A changed payload cannot reuse the original approval' ($newReview -ne $review)
    $null = Req Post "/sensitive/approvals/$newReview/decision" 204 $owner @{approved=$false} $grant
    $null = Req Put $bankPath 204 $alpha $changed -Confirm
    Assert-Fact 'Only an approved exact bank change executes' ((Req Get $bankPath 200 $alpha).bankAccountNo -eq $changed.bankAccountNo)
    $null = Req Put $bankPath 202 $alpha $changed -Confirm

    $marker = 'Local verified salary ' + ([guid]::NewGuid().ToString('N').Substring(0,8))
    $rate = [ordered]@{name=$marker;type='EARNING';abbr='LOCAL'}
    $component = (Req Post '/payroll/components' 202 $alpha $rate -Confirm).approvalId
    $null = Req Post "/sensitive/approvals/$component/decision" 204 $owner @{approved=$true} $grant
    Assert-Fact 'Approved salary configuration executes normally' (
        (Req Post '/payroll/components' 200 $alpha $rate -Confirm).name -eq $marker)
    $foreignGrant = SelectCompany $b $grant
    $null = Req Get '/payroll/runs' 410 $owner $null $grant
    $null = Req Get '/payroll/runs' 428 $owner $null $foreignGrant
    Unlock $owner $ownerPassword $foreignGrant
    $null = Req Post "/sensitive/approvals/$review/decision" 404 $owner @{approved=$true} $foreignGrant
    $null = Req Get $bankPath 404 $owner $null $foreignGrant
    $access = Company access admin
    Unlock $access $password
    $draft = (Req Get '/payroll/runs?page=0&size=20' 200 $access).content | Where-Object periodYear -eq 2098
    $accessCompany = ($companies | Where-Object code -eq 'isolation-access').id
    $accessGrant = SelectCompany $accessCompany $foreignGrant
    Unlock $owner $ownerPassword $accessGrant
    $submit = "/payroll/runs/$($draft.id)/submit"
    if ($draft.status -eq 'DRAFT') {
        $payrollReview = (Req Post $submit 202 $access $null $null -Confirm).approvalId
        Assert-Fact 'Pending payroll approval leaves the run in draft' (
            ((Req Get '/payroll/runs?page=0&size=20' 200 $access).content | Where-Object id -eq $draft.id).status -eq 'DRAFT')
        $null = Req Post "/sensitive/approvals/$payrollReview/decision" 204 $owner @{approved=$true} $accessGrant
        Assert-Fact 'Approved payroll goes through the existing submission workflow' (
            (Req Post $submit 200 $access $null $null -Confirm).status -eq 'SUBMITTED')
    } else {
        Assert-Fact 'Previously approved synthetic payroll remains submitted' ($draft.status -eq 'SUBMITTED')
    }
    $null = Req Post $submit 404 $bravo $null $null -Confirm
    Write-Host "$passed financial access and approval checks passed."
} finally {
    foreach ($t in $tokens.Values) { $null = Req Post '/auth/logout' 200 $t }
    if ($owner) { $null = Req Post '/platform/logout' 200 $owner }
}
