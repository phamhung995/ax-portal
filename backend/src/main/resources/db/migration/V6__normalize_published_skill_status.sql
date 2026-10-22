-- Older sample data used APPROVED for the skill state. The workflow uses
-- PUBLISHED for skills and APPROVED only for approval decisions.
UPDATE skill SET status = 'PUBLISHED' WHERE status = 'APPROVED';
