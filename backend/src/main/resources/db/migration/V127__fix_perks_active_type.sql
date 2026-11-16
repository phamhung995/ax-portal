-- V127: perks.active was CHAR(1); Hibernate validates a String(length=1)
-- column as VARCHAR(1). Align the column type (values 'Y'/'N' unchanged).
ALTER TABLE perks ALTER COLUMN active TYPE VARCHAR(1);
