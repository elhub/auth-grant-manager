--changeset elhub:33
ALTER TABLE auth.authorization_audit_log
  ADD COLUMN id UUID NOT NULL;

ALTER TABLE auth.authorization_audit_log
  DROP CONSTRAINT authorization_audit_log_pkey;

ALTER TABLE auth.authorization_audit_log
  ADD PRIMARY KEY (id);
