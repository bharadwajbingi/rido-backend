-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- User Profiles
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id BIGINT NOT NULL UNIQUE, -- Foreign key to Auth Service (logical)
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    photo_url TEXT,
    role VARCHAR(50) NOT NULL, -- RIDER, DRIVER, ADMIN
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_user_profiles_phone ON user_profiles(phone);
CREATE INDEX idx_user_profiles_email ON user_profiles(email);

-- Rider Addresses
CREATE TABLE rider_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rider_id BIGINT NOT NULL,
    label VARCHAR(100) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_rider_addresses_rider_id ON rider_addresses(rider_id);

-- Driver Documents
CREATE TABLE driver_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL, -- LICENSE, REGISTRATION, etc.
    document_number VARCHAR(100) NOT NULL,
    url TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reviewed_by BIGINT -- Admin User ID
);

CREATE INDEX idx_driver_documents_driver_id ON driver_documents(driver_id);
CREATE INDEX idx_driver_documents_status ON driver_documents(status);

-- Driver Stats
CREATE TABLE driver_stats (
    driver_id BIGINT PRIMARY KEY, -- Same as user_id
    total_trips INT DEFAULT 0,
    cancelled_trips INT DEFAULT 0,
    rating DOUBLE PRECISION DEFAULT 5.0,
    earnings DOUBLE PRECISION DEFAULT 0.0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audit Logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor BIGINT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity_id ON audit_logs(entity_id);
