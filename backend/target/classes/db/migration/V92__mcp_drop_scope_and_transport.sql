-- Flyway migration V92: MCP simplification (PO confirmed 2026-11-03).
--
-- 1. Drop visibility scope entirely:
--    - mcp_master.public_scope, mcp_version.public_scope
--    - index idx_mcp_scope
--    Every MCP is implicitly PUBLIC once approved; visibility = APPROVED
--    + not archived + not soft-deleted (no more part/group overlap rules).
--
-- 2. Drop MCP Type (transport) + config parsing columns:
--    - mcp_master.transport, index idx_mcp_transport
--    - mcp_version.transport, config_template, prerequisites, credential_fields
--    An MCP is now a single uploaded file (no Remote/Local distinction,
--    no mcp.json config parsing, no credential field declarations).

ALTER TABLE mcp_master DROP CONSTRAINT IF EXISTS mcp_master_public_scope_check;
ALTER TABLE mcp_master DROP CONSTRAINT IF EXISTS mcp_master_transport_check;
ALTER TABLE mcp_version DROP CONSTRAINT IF EXISTS mcp_version_transport_check;
ALTER TABLE mcp_version DROP CONSTRAINT IF EXISTS mcp_version_public_scope_check;

DROP INDEX IF EXISTS idx_mcp_scope;
DROP INDEX IF EXISTS idx_mcp_transport;

ALTER TABLE mcp_master DROP COLUMN IF EXISTS public_scope;
ALTER TABLE mcp_master DROP COLUMN IF EXISTS transport;

ALTER TABLE mcp_version DROP COLUMN IF EXISTS transport;
ALTER TABLE mcp_version DROP COLUMN IF EXISTS config_template;
ALTER TABLE mcp_version DROP COLUMN IF EXISTS prerequisites;
ALTER TABLE mcp_version DROP COLUMN IF EXISTS credential_fields;
ALTER TABLE mcp_version DROP COLUMN IF EXISTS public_scope;
