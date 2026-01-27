--changeset elhub:16
CREATE TABLE auth.authorization_document
(
  id             UUID                                   NOT NULL,
  type           auth.authorization_document_type       NOT NULL,
  file           BYTEA                                  NOT NULL,
  status         auth.authorization_document_status     NOT NULL,
  requested_by   UUID                                   NOT NULL,
  requested_from UUID                                   NOT NULL,
  requested_to   UUID                                   NOT NULL,
  created_at     TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  updated_at     TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  valid_to       TIMESTAMP WITH TIME ZONE               NOT NULL,
  PRIMARY KEY (id)
);
