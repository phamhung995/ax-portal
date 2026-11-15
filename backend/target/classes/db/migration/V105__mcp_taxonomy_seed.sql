-- Seed MCP taxonomy (categories + stages) with the same values as the Skill
-- taxonomy, so MCP Management is usable out of the box (Process Map pipeline,
-- Add MCP form). Admins can still manage them via /admin/mcp-taxonomy.
-- Idempotent: only seeds when the tables are empty.
--
-- NOTE (Flyway): V105 - checked flyway_schema_history: repo max = 104, dev DB max = 104.

INSERT INTO mcp_category (name, created_at)
SELECT v.name, CURRENT_TIMESTAMP
FROM (VALUES
  ('Quality'),
  ('Security'),
  ('DevOps'),
  ('AI/ML'),
  ('Documentation'),
  ('Other')
) AS v(name)
WHERE NOT EXISTS (SELECT 1 FROM mcp_category);

INSERT INTO mcp_stage (name, sort_order, description, created_at)
SELECT v.name, v.sort_order, v.description, CURRENT_TIMESTAMP
FROM (VALUES
  ('Receive SR', 1, 'Receive business requirements from customers'),
  ('Planning', 2, 'Define how the work will be carried out, when, and by whom.'),
  ('A&D', 3, 'Architecting solutions and designing interfaces'),
  ('Code', 4, 'Implementing features and writing code'),
  ('Test', 5, 'Verifying quality through manual and automated tests'),
  ('Final Inspecs', 6, 'Conduct a comprehensive quality check before release.'),
  ('Deployment', 7, 'Releasing builds to staging and production'),
  ('Close SR', 8, 'Change the status to Closed and hand over any related document')
) AS v(name, sort_order, description)
WHERE NOT EXISTS (SELECT 1 FROM mcp_stage);
