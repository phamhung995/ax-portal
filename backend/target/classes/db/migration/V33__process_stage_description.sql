-- Flyway migration V33: process stage short description
-- Shown under the stage name in the Process Map pipeline.

ALTER TABLE process_stage ADD COLUMN description VARCHAR(200);

-- Descriptions for the default stages seeded in V31.
UPDATE process_stage SET description = 'Gathering and clarifying business requirements' WHERE name = 'Requirements';
UPDATE process_stage SET description = 'Architecting solutions and designing interfaces' WHERE name = 'Design';
UPDATE process_stage SET description = 'Implementing features and writing code' WHERE name = 'Development';
UPDATE process_stage SET description = 'Verifying quality through manual and automated tests' WHERE name = 'Testing / QA';
UPDATE process_stage SET description = 'Releasing builds to staging and production' WHERE name = 'Deployment';
UPDATE process_stage SET description = 'Monitoring, maintaining and supporting in production' WHERE name = 'Operations';
