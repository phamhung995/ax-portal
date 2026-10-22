# Isolated SDS desktop pilot

This additive directory does not change existing Store files, migrations or business tables. Java 21 and a local Maven runtime are required. The existing wrapper files are incomplete in this source snapshot; pass an already installed Maven path rather than editing them.

Start only a dedicated PostgreSQL database named `sds_desktop_pilot_*`. Before starting the application, `start.ps1` reads the database name, server address/port and database comment and compares them with the explicit JDBC URL and `DESKTOP_PILOT_MARKER`. It refuses remote URLs, the normal Store database, URL query parameters, missing/mismatched markers and an occupied HTTP port. It never creates a database or rewrites PostgreSQL authentication. Create the isolated database and marker deliberately before running it.

Set these environment variables in the launching process; do not commit their values:

- `DESKTOP_PILOT_DB_USERNAME`, `DESKTOP_PILOT_DB_PASSWORD`
- `DESKTOP_PILOT_MARKER`, matching a comment of the form `SDS_DESKTOP_PILOT_ISOLATED_<ID>`
- `DESKTOP_PILOT_STORAGE`, an absolute separate pilot storage folder
- `DESKTOP_PILOT_JWT_SECRET`, a fresh Base64 key of at least 64 random bytes

Run `./desktop-pilot/start.ps1 -DatabaseUrl <jdbc> -PsqlPath <psql.exe> -MavenPath <mvn.cmd>`. Add `-ValidateOnly` to check identity without starting or migrating anything. The process remains in the foreground so its caller owns shutdown. It binds HTTP to `127.0.0.1:8080/skm`, uses existing Flyway migrations with Hibernate `validate`, writes uploads below the supplied pilot storage and disables external Knox mail. Existing upstream development seeding can create synthetic default accounts only in this dedicated database; it is not production account provisioning.

Capture file inventory before adding files using `inventory.ps1 -Root <Store root> -Output <outside source baseline.json>`. Read schema with `psql -X -At -v ON_ERROR_STOP=1 -f desktop-pilot/schema.sql` after initial upstream bootstrap, before desktop migrations. Capture both again for `audit.ps1 -BeforeFiles ... -AfterFiles ... -BeforeSchema ... -AfterSchema ...`. Existing paths/objects must remain byte-for-byte/definition-for-definition unchanged. New source is restricted to desktop directories/profile; generated `backend/target` build output is reported separately as permitted additions. New schema objects must belong to `desktop_*`; adding indexes or triggers to old tables is refused.

Run `guards.test.ps1`, `publish-validation.test.ps1` and `audit.test.ps1` for refusal cases. The desktop profile now exposes authenticated catalog, detail, taxonomy, release and package endpoints under `/skm/api/v1/desktop`. Trending/Hot return unavailable; All Time uses case-insensitive name then skill ID before pagination. Health alone does not prove contracts or AIPro acceptance.

Publish only on the verified dedicated database using `publish.ps1 -DatabaseUrl <jdbc> -PsqlPath <psql.exe> -StorageRoot <pilot-storage>/desktop-packages -Manifest <release.json> -Archive <archive.zip-or-7z> -Format zip|7z`. The manifest contains `skillId`, UUID `id`, positive `sequence`, `version`, safe `installName`, `publishedAt`, `changelog`, nullable `compatibleAgents` and nullable `requiredMcps`. Null means unknown; an empty MCP array means none required. The publisher generates the download filename from installName/format, validates archive magic, hashes the locked source and copies it to a fresh opaque storage identity before atomically publishing records. It never updates upstream business versions. Published IDs and sequences cannot be overwritten. Identical retries verify saved bytes; conflicting retries fail. A failed transaction can retain its newly created unreferenced package for operator review; no automatic broad cleanup runs.

The package service uses `desktop.pilot.package-root`, deliberately distinct from `DESKTOP_PILOT_STORAGE` to avoid Spring environment binding overriding the appended desktop-packages directory. It verifies size/SHA-256 and streams the same open file handle; Windows sharing blocks writes/deletion until transfer closes. Missing files and changed bytes fail explicitly. All release/package requests recheck current visibility and download permission.

The app repository provides `node scripts/test-sds-provider.mjs`, requiring `SDS_PROVIDER_EMAIL` and `SDS_PROVIDER_PASSWORD` in the process environment. It targets only loopback `/skm/`, validates live wire contracts, login/refresh, negative requests and exact synthetic ZIP/7z bytes. The opt-in Rust test `live_pilot_gateway_downloads_and_prepares_zip_and_sevenzip -- --ignored` exercises the actual app gateway and both decoders against the same pilot. These checks do not replace installed Windows app and real AIPro acceptance.

Raw file manifests also retain Git metadata for provenance. The source audit excludes `/.git/`: read-only Git commands may refresh the index/stat cache. This exclusion does not cover any Store source, settings, migrations or other files. Do not restore/reset a user's Git index to make a hash audit pass.
