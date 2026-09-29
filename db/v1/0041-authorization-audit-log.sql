--liquibase formatted sql
--changeset elhub:41
CREATE TABLE auth_v1.authorization_audit_log (
    authorization_grant_id UUID NOT NULL REFERENCES auth_v1.authorization_grant (id) ON DELETE CASCADE,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by VARCHAR(64) NOT NULL,
    value_changed VARCHAR(64),
    value_before VARCHAR(255),
    value_after VARCHAR(255),
    message TEXT,
    PRIMARY KEY (authorization_grant_id, changed_at)
);
