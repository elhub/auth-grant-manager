-- changeset elhub:12
CREATE TABLE auth.authorization_audit_log (
    authorization_grant_id UUID,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changed_by VARCHAR(64) NOT NULL,
    value_changed VARCHAR(64),
    value_before VARCHAR(255),
    value_after VARCHAR(255),
    message TEXT
);
