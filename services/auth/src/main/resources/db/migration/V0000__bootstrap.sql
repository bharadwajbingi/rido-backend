-- Bootstrap migration: Schema and extensions setup
-- This runs before all other migrations

-- Create schema if not exists (for multi-tenant or explicit schema setups)
CREATE SCHEMA IF NOT EXISTS auth;

-- Create required Postgres extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
