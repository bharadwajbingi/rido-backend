-- Change user_id from BIGINT to UUID to match Auth service

-- Enable UUID extension for uuid_generate_v4()
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Drop existing tables (since we're in development and can recreate)
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS driver_stats CASCADE;
DROP TABLE IF EXISTS driver_documents CASCADE;
DROP TABLE IF EXISTS rider_addresses CASCADE;
DROP TABLE IF EXISTS user_profiles CASCADE;

-- Recreate with UUID for user_id
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE, -- Changed from BIGINT to UUID
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    photo_url TEXT,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_phone ON user_profiles(phone);
CREATE INDEX idx_user_profiles_email ON user_profiles(email);

CREATE TABLE rider_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rider_id UUID NOT NULL, -- Changed from BIGINT to UUID
    label VARCHAR(100) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_rider_addresses_rider_id ON rider_addresses(rider_id);

CREATE TABLE driver_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id UUID NOT NULL, -- Changed from BIGINT to UUID
    type VARCHAR(50) NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reviewed_by UUID -- Changed from BIGINT to UUID
);

CREATE INDEX idx_driver_documents_driver_id ON driver_documents(driver_id);
CREATE INDEX idx_driver_documents_status ON driver_documents(status);

CREATE TABLE driver_stats (
    driver_id UUID PRIMARY KEY, -- Changed from BIGINT to UUID
    total_trips INT DEFAULT 0,
    cancelled_trips INT DEFAULT 0,
    rating DOUBLE PRECISION DEFAULT 0.0,
    earnings DOUBLE PRECISION DEFAULT 0.0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor UUID NOT NULL, -- Changed from BIGINT to UUID
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity_id ON audit_logs(entity_id);
