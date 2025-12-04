ALTER TABLE users
    ADD COLUMN failed_login_attempts INT DEFAULT 0,
    ADD COLUMN account_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN lockout_end_time TIMESTAMPTZ NULL,
    ADD COLUMN locked_until TIMESTAMPTZ NULL;
