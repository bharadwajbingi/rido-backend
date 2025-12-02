ALTER TABLE refresh_tokens
ALTER COLUMN revoked SET DEFAULT false;

UPDATE refresh_tokens
SET revoked = false
WHERE revoked IS NULL;

ALTER TABLE refresh_tokens
ALTER COLUMN revoked SET NOT NULL;
