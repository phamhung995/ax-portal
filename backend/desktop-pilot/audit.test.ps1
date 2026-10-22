Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$scratch=Join-Path ([IO.Path]::GetTempPath()) ('sds-pilot-audit-'+[guid]::NewGuid())
New-Item -ItemType Directory -Path $scratch | Out-Null
function Save([string]$Name,$Value) { $Value | ConvertTo-Json -Depth 6 -AsArray | Set-Content -LiteralPath (Join-Path $scratch $Name) -Encoding utf8 }
$oldFile=[pscustomobject]@{path='/backend/pom.xml';size=5;sha256='baseline'}
$oldObject=[pscustomobject]@{kind='table';name='skills_master';definition='r'}
Save 'before-files.json' @($oldFile); Save 'before-schema.json' @($oldObject)
function Check([bool]$ShouldPass) {
    $passed=$true
    try { & (Join-Path $PSScriptRoot 'audit.ps1') -BeforeFiles (Join-Path $scratch 'before-files.json') -AfterFiles (Join-Path $scratch 'after-files.json') -BeforeSchema (Join-Path $scratch 'before-schema.json') -AfterSchema (Join-Path $scratch 'after-schema.json') | Out-Null } catch { $passed=$false }
    if($passed -ne $ShouldPass) { throw 'Audit did not distinguish additive and changed/deleted input.' }
}
Save 'after-files.json' @($oldFile,[pscustomobject]@{path='/backend/desktop-pilot/new.ps1';size=1;sha256='new'})
Save 'after-schema.json' @($oldObject,[pscustomobject]@{kind='table';name='desktop_release';definition='r'})
Check $true
Save 'after-files.json' @([pscustomobject]@{path=$oldFile.path;size=6;sha256='changed'}); Check $false
Save 'after-files.json' @(); Check $false
Save 'after-files.json' @($oldFile)
Save 'after-schema.json' @([pscustomobject]@{kind='table';name=$oldObject.name;definition='changed'}); Check $false
Save 'after-schema.json' @(); Check $false
Save 'after-schema.json' @($oldObject,[pscustomobject]@{kind='index';name='skills_master.desktop_extra';definition='new'}); Check $false
Save 'before-files.json' @($oldFile,[pscustomobject]@{path='/.git/index';size=1;sha256='old-git-cache'})
Save 'after-files.json' @($oldFile,[pscustomobject]@{path='/.git/index';size=2;sha256='refreshed-git-cache'})
Save 'after-schema.json' @($oldObject); Check $true
Write-Output "Additive audit tests passed (five changed/deleted/out-of-scope cases refused); synthetic files: $scratch"
