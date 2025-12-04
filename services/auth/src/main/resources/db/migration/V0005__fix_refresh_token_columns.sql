ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS revoked boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS device_id varchar,
    ADD COLUMN IF NOT EXISTS user_agent text,
    ADD COLUMN IF NOT EXISTS ip varchar,
    ADD COLUMN IF NOT EXISTS jti uuid,
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
