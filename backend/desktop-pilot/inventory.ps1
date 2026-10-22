param(
    [Parameter(Mandatory)][string] $Root,
    [Parameter(Mandatory)][string] $Output
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$absoluteRoot = (Resolve-Path -LiteralPath $Root).Path.TrimEnd('\','/')
if (Test-Path -LiteralPath $Output) { throw 'Refusing to replace an existing baseline.' }
$files = @(Get-ChildItem -LiteralPath $absoluteRoot -File -Force -Recurse | Sort-Object FullName | ForEach-Object {
    [pscustomobject] @{ path=$_.FullName.Substring($absoluteRoot.Length).Replace('\','/'); size=$_.Length; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
})
$files | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Output -Encoding utf8
Write-Output "Captured $($files.Count) files."
