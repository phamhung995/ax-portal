Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Convert-DesktopNames($ManifestObject,[string]$Field) {
    $property=$ManifestObject.PSObject.Properties[$Field]
    if($null -eq $property -or $null -eq $property.Value){return [pscustomobject]@{items=$null}}
    if($property.Value -isnot [Array] -or $property.Value.Count -gt 128){throw 'Invalid release requirements/compatibility array'}
    $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($name in $property.Value){if($name -isnot [string] -or [string]::IsNullOrWhiteSpace($name) -or $name.Length -gt 256 -or -not $names.Add($name)){throw 'Invalid or duplicate release requirement/compatibility name'}}
    return [pscustomobject]@{items=@($property.Value)}
}
function ConvertTo-DesktopReleaseMetadata($ManifestObject) {
    [long]$skillId=0;[long]$sequence=0;[guid]$releaseId=[guid]::Empty;[DateTimeOffset]$published=[DateTimeOffset]::MinValue
    if(-not [long]::TryParse([string]$ManifestObject.skillId,[ref]$skillId) -or $skillId -lt 1 -or -not [long]::TryParse([string]$ManifestObject.sequence,[ref]$sequence) -or $sequence -lt 1 -or -not [guid]::TryParse([string]$ManifestObject.id,[ref]$releaseId)){throw 'Invalid skill ID, release UUID or sequence'}
    $installName=[string]$ManifestObject.installName
    $stem=$installName.Split('.')[0].ToUpperInvariant()
    if($installName -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$' -or $installName.EndsWith('.') -or $stem -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$'){throw 'Install name must be one safe Windows folder name'}
    $version=[string]$ManifestObject.version;$changelog=[string]$ManifestObject.changelog
    if([string]::IsNullOrWhiteSpace($version) -or $version.Length -gt 128 -or $changelog.Length -gt 8192 -or -not [DateTimeOffset]::TryParse([string]$ManifestObject.publishedAt,[ref]$published)){throw 'Invalid version, timestamp or changelog'}
    $compatibility=Convert-DesktopNames $ManifestObject 'compatibleAgents';$requirements=Convert-DesktopNames $ManifestObject 'requiredMcps'
    return [pscustomobject][ordered]@{skillId=$skillId.ToString();id=$releaseId.ToString();sequence=$sequence;version=$version;installName=$installName;publishedAt=$published.ToUniversalTime().ToString('o');changelog=$changelog;compatibleAgents=$compatibility.items;requiredMcps=$requirements.items}
}
function Assert-DesktopArchiveHeader([string]$Format,[byte[]]$Header) {
    $hex=[Convert]::ToHexString($Header)
    if(($Format -ceq 'zip' -and ($hex.StartsWith('504B0304') -or $hex.StartsWith('504B0506'))) -or ($Format -ceq '7z' -and $hex.StartsWith('377ABCAF271C'))){return}
    throw 'Archive bytes do not match the declared ZIP/7z format'
}
function Assert-DesktopPlainDirectory([string]$Path) {
    $absolute=[IO.Path]::GetFullPath($Path)
    if(-not [IO.Path]::IsPathFullyQualified($Path) -or $absolute.StartsWith('\\')){throw 'Pilot storage requires an absolute local directory'}
    $cursor=[IO.DirectoryInfo]::new($absolute)
    while($null -ne $cursor){
        if($cursor.Exists -and ($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Pilot storage cannot traverse a directory link'}
        $cursor=$cursor.Parent
    }
}
