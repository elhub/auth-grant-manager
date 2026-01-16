--changeset elhub:20
CREATE TABLE auth.authorization_request
(
  id             UUID                                   NOT NULL,
  request_type   auth.authorization_request_type        NOT NULL,
  request_status auth.authorization_request_status      NOT NULL,
  requested_by   UUID                                   NOT NULL,
  requested_from UUID                                   NOT NULL,
  requested_to   UUID                                   NOT NULL,
  approved_by    UUID,
  valid_to       TIMESTAMP WITH TIME ZONE               NOT NULL,
  created_at     TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  updated_at     TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  PRIMARY KEY (id)
);
