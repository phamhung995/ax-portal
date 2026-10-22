$ErrorActionPreference = 'Stop'
$control = Join-Path $PSScriptRoot 'store-control.ps1'
if (-not (Test-Path -LiteralPath $control)) { throw 'Store control implementation is missing.' }
. $control -FunctionsOnly

$backend = Split-Path $PSScriptRoot -Parent
$app = [pscustomobject]@{ Name='java.exe'; CommandLine='java.exe com.skillmanagement.SkillManagementApplication --spring.profiles.active=desktop-pilot' }
$parent = [pscustomobject]@{ Name='java.exe'; CommandLine="java.exe org.codehaus.plexus.classworlds.launcher.Launcher -Dmaven.multiModuleProjectDirectory=$backend -Dspring-boot.run.profiles=desktop-pilot" }
if (-not (Test-PilotProcess $app $parent $backend)) { throw 'The pilot process should be recognized.' }
$badApp = [pscustomobject]@{ Name='java.exe'; CommandLine='java.exe com.skillmanagement.SkillManagementApplication --spring.profiles.active=production' }
if (Test-PilotProcess $badApp $parent $backend) { throw 'A production process must not be stopped.' }
$otherParent = [pscustomobject]@{ Name='java.exe'; CommandLine='java.exe org.codehaus.plexus.classworlds.launcher.Launcher -Dmaven.multiModuleProjectDirectory=C:\other -Dspring-boot.run.profiles=desktop-pilot' }
if (Test-PilotProcess $app $otherParent $backend) { throw 'A pilot from another project must not be stopped.' }
if (Test-PilotProcess $app $null $backend) { throw 'A process without a verifiable parent must not be stopped.' }
Write-Output 'Store process safety tests passed.'
