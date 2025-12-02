-- 1) Rename old token column → token_hash
ALTER TABLE refresh_tokens
    RENAME COLUMN token TO token_hash;

-- 2) Add device_id (used in AuthService)
ALTER TABLE refresh_tokens
    ADD COLUMN device_id VARCHAR NULL;

-- 3) Add ip (used in AuthService)
ALTER TABLE refresh_tokens
    ADD COLUMN ip VARCHAR NULL;

-- 4) Create index for fast refresh token lookup
CREATE INDEX idx_refresh_tokens_hash
    ON refresh_tokens(token_hash);
