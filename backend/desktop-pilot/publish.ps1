param(
    [Parameter(Mandatory)][string]$DatabaseUrl,
    [Parameter(Mandatory)][string]$PsqlPath,
    [Parameter(Mandatory)][string]$StorageRoot,
    [Parameter(Mandatory)][string]$Manifest,
    [Parameter(Mandatory)][string]$Archive,
    [Parameter(Mandatory)][ValidateSet('zip','7z')][string]$Format
)
. (Join-Path $PSScriptRoot 'guards.ps1')
. (Join-Path $PSScriptRoot 'publish-validation.ps1')
$connection=ConvertTo-PilotConnection $DatabaseUrl
$identity=Get-PilotIdentity $connection $PsqlPath $env:DESKTOP_PILOT_DB_USERNAME
Assert-PilotIdentity $connection $identity $env:DESKTOP_PILOT_MARKER
Assert-DesktopPlainDirectory $StorageRoot
$manifestFile=Get-Item -LiteralPath $Manifest -Force
if($manifestFile.PSIsContainer -or $manifestFile.Length -gt 65536 -or ($manifestFile.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Invalid or oversized local release manifest'}
$metadata=ConvertTo-DesktopReleaseMetadata (Get-Content -LiteralPath $manifestFile.FullName -Raw -Encoding utf8 | ConvertFrom-Json)
$metadataJson=$metadata | ConvertTo-Json -Depth 12 -Compress -EscapeHandling EscapeNonAscii
if($metadataJson.Length -gt 12000){throw 'Pilot publication metadata exceeds 12000 encoded characters'}
$Format=$Format.ToLowerInvariant()
$fileName=$metadata.installName+'.'+$Format
$archiveFile=Get-Item -LiteralPath $Archive -Force
if($archiveFile.PSIsContainer -or ($archiveFile.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Archive must be a regular local file'}
Assert-DesktopPlainDirectory $archiveFile.DirectoryName

function Invoke-PilotQuery([string]$Sql,[hashtable]$Variables,[switch]$File) {
    $nativeArgs=@('-X','-w','-q','-h',$connection.host,'-p',[string]$connection.port,'-U',$env:DESKTOP_PILOT_DB_USERNAME,'-d',$connection.database,'-At','-v','ON_ERROR_STOP=1')
    foreach($name in $Variables.Keys){$nativeArgs+=@('-v',($name+'='+[string]$Variables[$name]))}
    # psql expands quoted variables in script input, but not in -c commands.
    # The temporary script contains only SQL placeholders, never bound values.
    $temporaryScript=$null
    if($File){$nativeArgs+=@('-f',$Sql)}else{
        $temporaryScript=[IO.Path]::GetTempFileName()
        [IO.File]::WriteAllText($temporaryScript,$Sql,[Text.UTF8Encoding]::new($false))
        $nativeArgs+=@('-f',$temporaryScript)
    }
    $oldPassword=[Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
    try{
        $env:PGPASSWORD=$env:DESKTOP_PILOT_DB_PASSWORD
        $answer=& $PsqlPath @nativeArgs
        if($LASTEXITCODE -ne 0){throw 'Publication database command failed; no existing release was overwritten'}
        return ($answer -join "`n")
    }finally{
        [Environment]::SetEnvironmentVariable('PGPASSWORD',$oldPassword,'Process')
        if($temporaryScript){Remove-Item -LiteralPath $temporaryScript -Force}
    }
}
$source=[IO.File]::Open($archiveFile.FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
$hasher=[Security.Cryptography.SHA256]::Create()
try{
    if($source.Length -lt 6 -or $source.Length -gt 104857600){throw 'Package must be at most 100 MiB and contain a valid archive header'}
    $header=New-Object byte[] 6
    if($source.Read($header,0,6) -ne 6){throw 'Archive header is incomplete'}
    Assert-DesktopArchiveHeader $Format $header
    $source.Position=0;$sha256=[Convert]::ToHexString($hasher.ComputeHash($source)).ToLowerInvariant();$source.Position=0
    $variables=@{release_id=$metadata.id;skill_id=$metadata.skillId;sequence=$metadata.sequence;metadata=$metadataJson;format=$Format;file_name=$fileName;size_bytes=$source.Length;sha256=$sha256}
    $query=@'
SELECT json_build_object('same',r.metadata_json::jsonb=:'metadata'::jsonb AND p.format=:'format' AND p.file_name=:'file_name' AND p.size_bytes=:'size_bytes'::bigint AND p.sha256=:'sha256','storageKey',p.storage_key)
FROM desktop_release r JOIN desktop_package p ON p.id=r.package_id WHERE r.id=:'release_id'::uuid
'@
    $existing=Invoke-PilotQuery $query $variables
    if(-not [string]::IsNullOrWhiteSpace($existing)){
        $record=$existing | ConvertFrom-Json
        if(-not $record.same){throw 'Release ID is already published with different immutable metadata or bytes'}
        [guid]$key=[guid]::Empty
        if(-not [guid]::TryParse([string]$record.storageKey,[ref]$key)){throw 'Existing package storage identity is invalid'}
        $existingPath=Join-Path $StorageRoot ($key.ToString()+'.pkg')
        $existingFile=Get-Item -LiteralPath $existingPath -Force
        if($existingFile.PSIsContainer -or ($existingFile.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Published package is unavailable; refusing overwrite'}
        $saved=[IO.File]::Open($existingFile.FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
        try{if($saved.Length -ne $source.Length -or [Convert]::ToHexString($hasher.ComputeHash($saved)).ToLowerInvariant() -cne $sha256){throw 'Published package has changed; refusing overwrite'}}finally{$saved.Dispose()}
        Write-Output "Release $($metadata.id) already published with identical bytes/metadata; unchanged."
        return
    }
    [IO.Directory]::CreateDirectory([IO.Path]::GetFullPath($StorageRoot)) | Out-Null
    Assert-DesktopPlainDirectory $StorageRoot
    $packageId=[guid]::NewGuid().ToString();$storageKey=[guid]::NewGuid().ToString()
    $newPath=Join-Path $StorageRoot ($storageKey+'.pkg')
    $destination=[IO.File]::Open($newPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{$source.CopyTo($destination,65536);$destination.Flush($true)}finally{$destination.Dispose()}
    [IO.File]::SetAttributes($newPath,[IO.File]::GetAttributes($newPath) -bor [IO.FileAttributes]::ReadOnly)
    $variables.package_id=$packageId;$variables.storage_key=$storageKey;$variables.database=$connection.database;$variables.marker=$env:DESKTOP_PILOT_MARKER;$variables.port=$connection.port
    try{Invoke-PilotQuery (Join-Path $PSScriptRoot 'publish.sql') $variables -File | Out-Null}catch{throw "Publication failed. The newly created unreferenced package is retained for review: $newPath"}
    Write-Output "Published release $($metadata.id); skill=$($metadata.skillId); sequence=$($metadata.sequence); format=$Format; bytes=$($source.Length); sha256=$sha256"
}finally{$hasher.Dispose();$source.Dispose()}
