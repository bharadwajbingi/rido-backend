-- Remove deprecated/unused lockout fields from users table
-- Issue #57: Duplicate lockout fields causing inconsistent behavior
-- Only locked_until is actively used by LoginAttemptService

ALTER TABLE users
    DROP COLUMN IF EXISTS failed_login_attempts,
    DROP COLUMN IF EXISTS account_locked,
    DROP COLUMN IF EXISTS lockout_end_time;
