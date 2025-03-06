--changeset elhub:2
CREATE TABLE consent.consent_request  (
  id VARCHAR(36) PRIMARY KEY,
  end_user_id VARCHAR(36),
  requested_by VARCHAR(36),
  created_at TIMESTAMP
);
