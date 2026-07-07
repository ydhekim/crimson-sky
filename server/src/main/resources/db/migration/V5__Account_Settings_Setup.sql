UPDATE accounts
SET settings = '{
  "volume_master": 0.5,
  "language": "en_US",
  "fullscreen": true,
  "resolution": "1280x720"
}'::jsonb
WHERE settings IS NULL;

UPDATE accounts
SET settings = jsonb_set(settings, '{resolution}', '"1280x720"'::jsonb, true)
WHERE settings IS NOT NULL AND NOT (settings ? 'resolution');

ALTER TABLE accounts ALTER COLUMN settings DROP DEFAULT;

ALTER TABLE accounts ALTER COLUMN settings SET NOT NULL;
