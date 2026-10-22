param(
    [Parameter(Mandatory)][string] $BeforeFiles,
    [Parameter(Mandatory)][string] $AfterFiles,
    [Parameter(Mandatory)][string] $BeforeSchema,
    [Parameter(Mandatory)][string] $AfterSchema
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Assert-Unchanged($Before,$After,[scriptblock] $Key,[scriptblock] $Value,[scriptblock] $AllowedAddition,[string] $Label) {
    $old=@{}; $current=@{}
    foreach($item in @($Before)) { $old[(& $Key $item)]=(& $Value $item) }
    foreach($item in @($After)) { $current[(& $Key $item)]=(& $Value $item) }
    foreach($id in $old.Keys) {
        if(-not $current.ContainsKey($id)) { throw "$Label removed: $id" }
        if($current[$id] -cne $old[$id]) { throw "$Label changed: $id" }
    }
    $added=0
    foreach($item in @($After)) {
        $id=& $Key $item
        if(-not $old.ContainsKey($id)) {
            if(-not (& $AllowedAddition $item)) { throw "$Label addition outside desktop scope: $id" }
            $added++
        }
    }
    return $added
}
$before=Get-Content -LiteralPath $BeforeFiles -Raw | ConvertFrom-Json
$after=Get-Content -LiteralPath $AfterFiles -Raw | ConvertFrom-Json
# Git refreshes its index/stat cache even for read-only status. It is repository
# bookkeeping, not Store source. Preserve the raw inventories but compare source.
$before=@($before | Where-Object { $_.path -cnotmatch '^/\.git/' })
$after=@($after | Where-Object { $_.path -cnotmatch '^/\.git/' })
$fileCount=Assert-Unchanged $before $after {param($x) $x.path} {param($x) "$($x.size):$($x.sha256)"} {param($x)
    $x.path -cmatch '^/backend/(desktop-pilot/|src/(main|test)/java/com/skillmanagement/desktop/|src/main/resources/(application-desktop-pilot\.yml$|META-INF/spring\.factories$|db/desktop-migration/)|src/test/resources/desktop/|target/)'
} 'File'
$before=Get-Content -LiteralPath $BeforeSchema -Raw | ConvertFrom-Json
$after=Get-Content -LiteralPath $AfterSchema -Raw | ConvertFrom-Json
$schemaCount=Assert-Unchanged $before $after {param($x) "$($x.kind):$($x.name)"} {param($x) $x.definition} {param($x) $x.name -cmatch '^desktop_'} 'Schema object'
Write-Output "Additive audit passed: existing source files and schema unchanged; $fileCount added files (including generated target output), $schemaCount desktop schema objects. Git bookkeeping excluded; raw inventories retained."
