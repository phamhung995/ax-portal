param(
    [Parameter(Mandatory)][string] $MavenPath,
    [string] $JavaHome = 'C:/Program Files/Java/jdk-21',
    [string] $Test = 'DesktopUpdatesTest',
    [Parameter(Mandatory)][string] $OutputDirectory
)
$ErrorActionPreference = 'Stop'
# Build a byte-identical source copy so Maven never touches pre-existing target files.
$backend = (Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
if (Test-Path -LiteralPath $OutputDirectory) { throw 'OutputDirectory must be new.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
Copy-Item -LiteralPath (Join-Path $backend 'pom.xml') -Destination $OutputDirectory
Copy-Item -LiteralPath (Join-Path $backend 'src') -Destination $OutputDirectory -Recurse
$env:JAVA_HOME = $JavaHome
Push-Location $OutputDirectory
try {
    $arguments = @('-B','-ntp')
    if ($Test -ne 'all') { $arguments += "-Dtest=$Test" }
    & $MavenPath @arguments test *> (Join-Path $OutputDirectory 'maven.log')
    $code = $LASTEXITCODE
    Get-Content -LiteralPath (Join-Path $OutputDirectory 'maven.log') -Tail 24
    Write-Output "Maven exit=$code; isolated build=$OutputDirectory"
    exit $code
} finally { Pop-Location }
