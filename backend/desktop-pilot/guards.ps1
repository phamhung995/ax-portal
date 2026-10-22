Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-PilotConnection([string] $DatabaseUrl) {
    if ($DatabaseUrl -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):([0-9]{1,5})/(sds_desktop_pilot_[a-z0-9_]+)$') {
        throw 'Pilot requires an explicit loopback JDBC URL and a dedicated sds_desktop_pilot_* database, without query parameters.'
    }
    $port = [int] $Matches[2]
    if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid PostgreSQL port.' }
    return [pscustomobject] @{ host=$Matches[1]; port=$port; database=$Matches[3] }
}

function Assert-PilotIdentity($Connection, $Identity, [string] $Marker) {
    if ($Marker -notmatch '^SDS_DESKTOP_PILOT_ISOLATED_[A-Z0-9_]+$' -or
        $Identity.database -cne $Connection.database -or
        $Identity.host -notin @('127.0.0.1','::1') -or
        [int] $Identity.port -ne $Connection.port -or
        $Identity.marker -cne $Marker) {
        throw 'Dedicated pilot database identity/marker did not match; no application or mutation was started.'
    }
}

function Get-PilotIdentity($Connection, [string] $PsqlPath, [string] $Username) {
    if (-not $env:DESKTOP_PILOT_DB_PASSWORD) { throw 'DESKTOP_PILOT_DB_PASSWORD is required.' }
    $previous = [Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
    try {
        $env:PGPASSWORD = $env:DESKTOP_PILOT_DB_PASSWORD
        $query = "SELECT json_build_object('database',current_database(),'host',inet_server_addr(),'port',inet_server_port(),'marker',shobj_description(oid,'pg_database')) FROM pg_database WHERE datname=current_database()"
        $body = & $PsqlPath -X -w -h $Connection.host -p $Connection.port -U $Username -d $Connection.database -At -v ON_ERROR_STOP=1 -c $query
        if ($LASTEXITCODE -ne 0) { throw 'Pilot database identity read failed.' }
        return ($body | ConvertFrom-Json)
    } finally {
        [Environment]::SetEnvironmentVariable('PGPASSWORD',$previous,'Process')
    }
}
