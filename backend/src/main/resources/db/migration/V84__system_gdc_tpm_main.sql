-- Flyway migration V84: system_master.gdc_tpm_main + i18n column label.
--
-- The Systems List import gains an optional "GDC TPM(Main)" column
-- (system-level, like GDC Group / GDC Part). The column is stored on
-- system_master and shown in the Systems tab grid.

ALTER TABLE system_master
    ADD COLUMN IF NOT EXISTS gdc_tpm_main VARCHAR(255);

INSERT INTO translation_keys (key, description) VALUES
    ('app.org.colGdcTpmMain', 'Systems tab: GDC TPM(Main) column label')
ON CONFLICT (key) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'en', v FROM translation_keys, (VALUES
    ('app.org.colGdcTpmMain', 'GDC TPM(Main)')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'vi', v FROM translation_keys, (VALUES
    ('app.org.colGdcTpmMain', 'GDC TPM(Main)')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;

INSERT INTO translations (key_id, language, value)
SELECT id, 'ko', v FROM translation_keys, (VALUES
    ('app.org.colGdcTpmMain', 'GDC TPM(Main)')
) AS t(key, v)
WHERE translation_keys.key = t.key
ON CONFLICT (key_id, language) DO NOTHING;
