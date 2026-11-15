-- Drop MCP archive columns (FR-MCP-009 removed 2026-11-05).
-- Archive/Unarchive is gone: an APPROVED MCP can be deleted directly as long
-- as no skill references it (SKILL_REFERENCE rule stays).
ALTER TABLE mcp_master DROP COLUMN archived_yn;
ALTER TABLE mcp_master DROP COLUMN archived_by;
ALTER TABLE mcp_master DROP COLUMN archived_at;
ALTER TABLE mcp_master DROP COLUMN archived_reason;
DROP INDEX IF EXISTS idx_mcp_archived;

-- Remove archive i18n keys (added by V93 + V94).
DELETE FROM translations
WHERE key_id IN (
    SELECT id FROM translation_keys WHERE key IN (
        'app.mcp.archive',
        'app.mcp.archiveReasonPh',
        'app.mcp.archiveTitle',
        'app.mcp.archived',
        'app.mcp.archivedAlert',
        'app.mcp.archivedToast',
        'app.mcp.unarchive',
        'app.mcp.unarchivedToast',
        'app.skills.mcpUnavailableArchived',
        'app.skills.mcpUnavailableArchivedWarn'
    )
);
DELETE FROM translation_keys WHERE key IN (
    'app.mcp.archive',
    'app.mcp.archiveReasonPh',
    'app.mcp.archiveTitle',
    'app.mcp.archived',
    'app.mcp.archivedAlert',
    'app.mcp.archivedToast',
    'app.mcp.unarchive',
    'app.mcp.unarchivedToast',
    'app.skills.mcpUnavailableArchived',
    'app.skills.mcpUnavailableArchivedWarn'
);
