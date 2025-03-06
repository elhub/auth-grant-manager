--changeset elhub:2
CREATE TABLE consent.consent_request  (
  id VARCHAR(36) PRIMARY KEY,
  end_user_id VARCHAR(36),
  requested_by VARCHAR(36),
  created_at TIMESTAMP
);

--changeset elhub:3
CREATE TABLE consent.authorization_grant  (
  id VARCHAR(36) PRIMARY KEY NOT NULL,
  granted_for VARCHAR(36) NOT NULL,
  granted_by VARCHAR(36) NOT NULL,
  granted_at TIMESTAMP NOT NULL
);
