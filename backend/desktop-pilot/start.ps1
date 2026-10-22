param(
    [Parameter(Mandatory)][string] $DatabaseUrl,
    [Parameter(Mandatory)][string] $PsqlPath,
    [Parameter(Mandatory)][string] $MavenPath,
    [switch] $ValidateOnly
)
. (Join-Path $PSScriptRoot 'guards.ps1')
$connection = ConvertTo-PilotConnection $DatabaseUrl
if (-not $env:DESKTOP_PILOT_DB_USERNAME -or -not $env:DESKTOP_PILOT_MARKER) { throw 'Pilot DB username and marker environment variables are required.' }
$identity = Get-PilotIdentity $connection $PsqlPath $env:DESKTOP_PILOT_DB_USERNAME
Assert-PilotIdentity $connection $identity $env:DESKTOP_PILOT_MARKER
Write-Output "Verified isolated database $($identity.database) on loopback port $($identity.port)."
if ($ValidateOnly) { return }
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) { throw 'Pilot HTTP port 8080 is occupied; refusing to replace another service.' }
foreach ($name in @('DESKTOP_PILOT_STORAGE','DESKTOP_PILOT_JWT_SECRET')) {
    if (-not [Environment]::GetEnvironmentVariable($name,'Process')) { throw "$name is required." }
}
$env:DESKTOP_PILOT_JDBC_URL = $DatabaseUrl
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & $MavenPath -B spring-boot:run '-Dspring-boot.run.profiles=desktop-pilot'
    if ($LASTEXITCODE -ne 0) { throw 'Pilot Store process failed. See the application log.' }
} finally { Pop-Location }
