-- Flyway migration V97: drop the notification_dead_letter table (PO 2026-11).
--
-- The Dead Letters admin menu + viewer (FR-MCP-022) was removed. Notification
-- failures now retry 3x and are dropped with an error log (Q-21, simplified).
-- The table and its indexes are dead weight — drop them.
--
-- NOTE (Flyway): renamed V95 -> V97 (2026-11-04) — V95 was already applied to
-- the shared DB by V95__version_update_confirm_i18n, and V96 is taken by
-- "mcp edit approved files hint" in flyway_schema_history.

DROP TABLE IF EXISTS notification_dead_letter CASCADE;
