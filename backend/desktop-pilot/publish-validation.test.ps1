$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'publish-validation.ps1')
function Manifest {
    [pscustomobject]@{skillId='1';id='d854d70e-c2a5-41a0-b643-20d3d4129df7';sequence=1;version='1.0.0';installName='sds-pilot';publishedAt='2026-10-17T00:00:00Z';changelog='Synthetic';compatibleAgents=$null;requiredMcps=@()}
}
function Rejects([scriptblock]$Action) { $refused=$false;try{& $Action}catch{$refused=$true};if(-not $refused){throw 'Invalid release input was accepted'} }
$metadata=ConvertTo-DesktopReleaseMetadata (Manifest)
if($null -eq $metadata.requiredMcps -or $metadata.requiredMcps.Count -ne 0 -or $null -ne $metadata.compatibleAgents){throw 'Empty versus unknown requirements were lost'}
foreach($name in @('../escape','CON','foo.','name with spaces')){ $bad=Manifest;$bad.installName=$name;Rejects {ConvertTo-DesktopReleaseMetadata $bad} }
$bad=Manifest;$bad.sequence=0;Rejects {ConvertTo-DesktopReleaseMetadata $bad}
$bad=Manifest;$bad.skillId='1; DROP TABLE skills_master';Rejects {ConvertTo-DesktopReleaseMetadata $bad}
$bad=Manifest;$bad.id='../release';Rejects {ConvertTo-DesktopReleaseMetadata $bad}
$bad=Manifest;$bad.requiredMcps=@('jira','jira');Rejects {ConvertTo-DesktopReleaseMetadata $bad}
$good=Manifest;$good.requiredMcps=@('jira','Jira');$metadata=ConvertTo-DesktopReleaseMetadata $good
if($metadata.requiredMcps.Count -ne 2){throw 'MCP names must remain case sensitive'}
Assert-DesktopArchiveHeader zip ([byte[]]@(0x50,0x4b,0x03,0x04,0,0))
Assert-DesktopArchiveHeader 7z ([byte[]]@(0x37,0x7a,0xbc,0xaf,0x27,0x1c))
Rejects {Assert-DesktopArchiveHeader zip ([byte[]]@(0x37,0x7a,0xbc,0xaf,0x27,0x1c))}
Rejects {Assert-DesktopArchiveHeader tar ([byte[]]@(0,0,0,0,0,0))}
Write-Output 'Desktop publish validation passed: traversal/reserved names/IDs/sequence/MCP duplicates/formats refused; empty/unknown and case preserved.'
