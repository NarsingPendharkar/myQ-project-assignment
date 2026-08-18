-- V1__initial_schema.sql
-- Initial schema for Audit Log Service (Scenario A)
-- Creates core tables for audit events and user management

-- Users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- Audit Events table with hash chain
CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    previous_hash VARCHAR(64) NOT NULL,
    chain_position BIGINT NOT NULL UNIQUE,
    archived BOOLEAN DEFAULT false,
    archived_at TIMESTAMP,
    redaction_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Performance indexes for Scenario A
CREATE INDEX IF NOT EXISTS idx_audit_events_chain_position ON audit_events(chain_position);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor_id ON audit_events(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_resource_type_id ON audit_events(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_type ON audit_events(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_events_timestamp ON audit_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_events_archived ON audit_events(archived);

-- Constraints
ALTER TABLE audit_events ADD CONSTRAINT chk_event_type_not_empty CHECK (event_type <> '');
ALTER TABLE audit_events ADD CONSTRAINT chk_actor_id_not_empty CHECK (actor_id <> '');
ALTER TABLE audit_events ADD CONSTRAINT chk_resource_type_not_empty CHECK (resource_type <> '');
ALTER TABLE audit_events ADD CONSTRAINT chk_resource_id_not_empty CHECK (resource_id <> '');
ALTER TABLE audit_events ADD CONSTRAINT chk_payload_not_empty CHECK (payload IS NOT NULL);
ALTER TABLE audit_events ADD CONSTRAINT chk_content_hash_length CHECK (LENGTH(content_hash) = 64);
ALTER TABLE audit_events ADD CONSTRAINT chk_previous_hash_length CHECK (LENGTH(previous_hash) = 64 OR previous_hash = 'GENESIS_HASH');
ALTER TABLE audit_events ADD CONSTRAINT chk_chain_position_positive CHECK (chain_position > 0);

-- Comment on tables
COMMENT ON TABLE audit_events IS 'Append-only audit event log with cryptographic hash chain for tamper detection';
COMMENT ON COLUMN audit_events.content_hash IS 'SHA-256 hash of event content (eventType || actorId || resourceType || resourceId || payload || timestamp)';
COMMENT ON COLUMN audit_events.previous_hash IS 'SHA-256 hash of previous record or GENESIS_HASH for first record';
COMMENT ON COLUMN audit_events.chain_position IS 'Sequential position in the chain (1, 2, 3, ...)';
