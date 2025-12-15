-- ==================================================
-- Auth Service Database Migration V0001
-- Production-Grade Schema Initialization
-- ==================================================

-- 1. Extensions FIRST (before schema)
CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA public;

-- 2. Schema creation
CREATE SCHEMA IF NOT EXISTS auth;

-- 3. Tables (FULLY QUALIFIED with schema prefix)
CREATE TABLE auth.audit_logs (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    entity VARCHAR(100),
    entity_id VARCHAR(100),
    action VARCHAR(100),
    actor UUID,
    metadata TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    device_id VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(500),
    ip_address VARCHAR(45),
    success BOOLEAN NOT NULL,
    "timestamp" TIMESTAMPTZ NOT NULL,
    user_agent VARCHAR(500),
    user_id UUID,
    username VARCHAR(150)
);

CREATE TABLE auth.users (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    username VARCHAR(150) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ DEFAULT now(),
    locked_until TIMESTAMPTZ
);

CREATE TABLE auth.refresh_tokens (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    device_id VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    ip VARCHAR(255),
    jti UUID,
    revoked BOOLEAN NOT NULL,
    token_hash VARCHAR(256) NOT NULL,
    user_agent VARCHAR(255),
    user_id UUID NOT NULL,
    rotated BOOLEAN NOT NULL DEFAULT false
);

-- 4. Indexes
CREATE INDEX idx_audit_event_type ON auth.audit_logs(event_type);
CREATE INDEX idx_audit_logs_entity_id ON auth.audit_logs(entity_id);
CREATE INDEX idx_audit_timestamp ON auth.audit_logs("timestamp");
CREATE INDEX idx_audit_user_id ON auth.audit_logs(user_id);
CREATE INDEX idx_audit_username ON auth.audit_logs(username);
