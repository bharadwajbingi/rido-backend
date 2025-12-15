-- Fix Postgres extensions for Testcontainers compatibility
-- Recreate extensions without schema specification to avoid permission issues

-- These may already exist from V0001, so we use IF NOT EXISTS
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
