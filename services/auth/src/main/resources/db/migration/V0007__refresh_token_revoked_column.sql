ALTER TABLE refresh_tokens
ADD COLUMN IF NOT EXISTS revoked boolean DEFAULT false;

ALTER TABLE refresh_tokens
ALTER COLUMN revoked SET NOT NULL;
