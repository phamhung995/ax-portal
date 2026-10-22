param(
    [ValidateSet('Start','Stop','Status')][string] $Action = 'Status',
    [switch] $FunctionsOnly
)
$ErrorActionPreference = 'Stop'

function Test-PilotProcess($App, $Parent, [string] $Backend) {
    if ($null -eq $App -or $null -eq $Parent) { return $false }
    if ($App.Name -ine 'java.exe' -or $Parent.Name -ine 'java.exe') { return $false }
    if ($App.CommandLine -notmatch 'com\.skillmanagement\.SkillManagementApplication' -or
        $App.CommandLine -notmatch '(?:^|\s)--spring\.profiles\.active=desktop-pilot(?:\s|$)') { return $false }
    $pathPattern = [regex]::Escape($Backend)
    return $Parent.CommandLine -match 'org\.codehaus\.plexus\.classworlds\.launcher\.Launcher' -and
        $Parent.CommandLine -match '(?:^|\s)-Dspring-boot\.run\.profiles=desktop-pilot(?:\s|$)' -and
        $Parent.CommandLine -match ("-Dmaven\.multiModuleProjectDirectory=$pathPattern(?:`"|\s|$)")
}

if ($FunctionsOnly) { return }

$backend = Split-Path $PSScriptRoot -Parent
$launcher = Join-Path $PSScriptRoot 'start.ps1'
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$maven = Join-Path $env:LOCALAPPDATA 'Programs\Apache\Maven\apache-maven-3.10.0\bin\mvn.cmd'
$storage = Join-Path $env:LOCALAPPDATA 'SDS-Skills-Manager\pilot'
$databaseUrl = 'jdbc:postgresql://127.0.0.1:5432/sds_desktop_pilot_local'
$storeUrl = 'http://127.0.0.1:8080/skm'

function Get-StoreListener {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 1) { throw 'More than one process is listening on port 8080. Refusing to act.' }
    if ($listeners.Count -eq 0) { return $null }
    $entry = $listeners[0]
    $app = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.OwningProcess)"
    if ($null -eq $app) { throw 'Could not inspect the process on port 8080.' }
    $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $($app.ParentProcessId)"
    return [pscustomobject]@{ Listener=$entry; App=$app; Verified=(Test-PilotProcess $app $parent $backend) }
}

function Get-StoreHealth {
    try { return (Invoke-RestMethod -Uri "$storeUrl/actuator/health" -TimeoutSec 3).status -eq 'UP' }
    catch { return $false }
}

try {
    $current = Get-StoreListener
    if ($Action -eq 'Status') {
        if ($null -eq $current) { Write-Host 'Store is stopped (port 8080 is free).' }
        elseif (-not $current.Verified) { Write-Host 'Port 8080 is occupied by an unrecognized process. No action taken.'; exit 1 }
        elseif (Get-StoreHealth) { Write-Host "Store is running: $storeUrl (PID $($current.App.ProcessId))." }
        else { Write-Host "Pilot process is listening, but Store health is not UP (PID $($current.App.ProcessId))."; exit 1 }
        exit 0
    }
    if ($Action -eq 'Stop') {
        if ($null -eq $current) { Write-Host 'Store is already stopped.'; exit 0 }
        if (-not $current.Verified) { throw 'Port 8080 belongs to an unrecognized process. Refusing to stop it.' }
        Stop-Process -Id $current.App.ProcessId -ErrorAction Stop
        for ($i=0; $i -lt 30; $i++) {
            Start-Sleep -Milliseconds 500
            $remaining = Get-StoreListener
            if ($null -eq $remaining) { Write-Host 'Store stopped. PostgreSQL was not stopped.'; exit 0 }
            if ($remaining.App.ProcessId -ne $current.App.ProcessId) { throw 'The port was taken by another process after stopping; no further action taken.' }
        }
        throw 'Store process was asked to stop, but port 8080 is still occupied.'
    }
    if ($null -ne $current) {
        if ($current.Verified -and (Get-StoreHealth)) { Write-Host "Store is already running: $storeUrl"; exit 0 }
        throw 'Port 8080 is occupied or the existing pilot is unhealthy. No new Store was started.'
    }
    foreach ($path in @($launcher,$psql,$maven,$storage)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Required file or directory not found: $path" }
    }
    $starting = @(Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($launcher) -and $_.CommandLine.Contains($databaseUrl) })
    if ($starting.Count -gt 0) { throw 'A pilot launcher is already running or starting. Wait and check status.' }
    $secure = Read-Host 'PostgreSQL password for postgres' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:DESKTOP_PILOT_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
    if (-not $env:DESKTOP_PILOT_DB_PASSWORD) { throw 'A PostgreSQL password is required.' }
    $env:DESKTOP_PILOT_DB_USERNAME = 'postgres'
    $env:DESKTOP_PILOT_MARKER = 'SDS_DESKTOP_PILOT_ISOLATED_LOCAL'
    $env:DESKTOP_PILOT_STORAGE = $storage
    $bytes = New-Object byte[] 64
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:DESKTOP_PILOT_JWT_SECRET = [Convert]::ToBase64String($bytes)
    & $launcher -DatabaseUrl $databaseUrl -PsqlPath $psql -MavenPath $maven -ValidateOnly
    $logId = [guid]::NewGuid().ToString('N')
    $outLog = Join-Path $storage "store-$logId.out.log"
    $errLog = Join-Path $storage "store-$logId.err.log"
    $argsList = @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',('"' + $launcher + '"'),'-DatabaseUrl',$databaseUrl,'-PsqlPath',('"' + $psql + '"'),'-MavenPath',('"' + $maven + '"'))
    $process = Start-Process -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -ArgumentList $argsList -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    Write-Host 'Starting Store; this can take a minute...'
    for ($i=0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 1
        $process.Refresh()
        $current = Get-StoreListener
        if ($null -ne $current -and $current.Verified -and (Get-StoreHealth)) { Write-Host "Store is running: $storeUrl"; exit 0 }
        if ($null -ne $current -and -not $current.Verified) { throw 'Another process took port 8080. Check the logs.' }
        if ($process.HasExited) { throw "Store launcher exited. Check logs: $outLog and $errLog" }
    }
    throw "Store did not become ready within 120 seconds. It may still be starting. Check logs: $outLog and $errLog"
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    exit 1
} finally {
    Remove-Item Env:DESKTOP_PILOT_DB_PASSWORD -ErrorAction SilentlyContinue
}
