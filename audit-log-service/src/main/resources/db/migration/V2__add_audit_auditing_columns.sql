-- V2__add_missing_columns.sql

-- AuditEvent JPA auditing columns
ALTER TABLE audit_events
    ADD COLUMN created_by VARCHAR(255) DEFAULT 'system';

ALTER TABLE audit_events
    ADD COLUMN updated_by VARCHAR(255) DEFAULT 'system';

-- User entity compatibility
ALTER TABLE users
    ADD COLUMN password VARCHAR(255);

UPDATE users
SET password = password_hash
WHERE password IS NULL;