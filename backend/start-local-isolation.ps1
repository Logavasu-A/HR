$ErrorActionPreference = 'Stop'

Push-Location $PSScriptRoot
$localSettings = @{
    HRMS_ISOLATION_RUNTIME_PASSWORD = './.local/local-isolation-runtime.secret'
    HRMS_ISOLATION_RLS_KEY = './.local/local-isolation-rls.secret'
}
$previousSettings = @{}
try {
    foreach ($name in $localSettings.Keys) {
        $previousSettings[$name] = [Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($previousSettings[$name])) {
            $secretFile = $localSettings[$name]
            if (-not (Test-Path -LiteralPath $secretFile -PathType Leaf)) {
                throw "Set $name or provide $secretFile before starting the backend."
            }
            $value = (Get-Content -LiteralPath $secretFile -Raw).Trim()
            if ([string]::IsNullOrWhiteSpace($value)) { throw "The file $secretFile is empty." }
            [Environment]::SetEnvironmentVariable($name, $value)
        }
    }

    # Local properties can override the profile's datasource URL.
    $profile = Get-Content -LiteralPath './src/resources/application-local-isolation.yml' -Raw
    $url = [regex]::Match($profile, '(?m)^\s+url:[ \t]*(\S+)').Groups[1].Value
    if ($url -notmatch '^jdbc:postgresql://(?:localhost|127\.0\.0\.1):5432/hrms_isolation_20261119$') {
        throw 'The local-isolation profile must target the verified local restore.'
    }
    $previousSettings['SPRING_DATASOURCE_URL'] = $env:SPRING_DATASOURCE_URL
    $env:SPRING_DATASOURCE_URL = $url

    .\mvn.cmd '-Dspring-boot.run.profiles=local-isolation' '-Dspring-boot.run.arguments=--app.platform.bootstrap-password-file=' spring-boot:run
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    foreach ($name in $previousSettings.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousSettings[$name])
    }
    Pop-Location
}
