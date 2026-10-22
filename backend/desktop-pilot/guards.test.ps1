. (Join-Path $PSScriptRoot 'guards.ps1')
function Rejects([scriptblock] $Action) {
    $rejected=$false
    try { & $Action } catch { $rejected=$true }
    if (-not $rejected) { throw 'Unsafe pilot configuration was accepted.' }
}
Rejects { ConvertTo-PilotConnection 'jdbc:postgresql://localhost:5432/skill_management_db' }
Rejects { ConvertTo-PilotConnection 'jdbc:postgresql://db.internal:5432/sds_desktop_pilot_test' }
Rejects { ConvertTo-PilotConnection 'jdbc:postgresql://127.0.0.1:5432/sds_desktop_pilot_test?currentSchema=public' }
$connection=ConvertTo-PilotConnection 'jdbc:postgresql://127.0.0.1:15432/sds_desktop_pilot_test'
$identity=[pscustomobject]@{database='sds_desktop_pilot_test';host='127.0.0.1';port=15432;marker='SDS_DESKTOP_PILOT_ISOLATED_TEST'}
Assert-PilotIdentity $connection $identity 'SDS_DESKTOP_PILOT_ISOLATED_TEST'
Rejects { Assert-PilotIdentity $connection $identity 'SDS_DESKTOP_PILOT_ISOLATED_WRONG' }
$identity.database='skill_management_db'
Rejects { Assert-PilotIdentity $connection $identity 'SDS_DESKTOP_PILOT_ISOLATED_TEST' }
Write-Output 'Pilot guard tests passed (five unsafe configurations refused).'
