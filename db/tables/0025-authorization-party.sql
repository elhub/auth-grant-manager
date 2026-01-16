--changeset elhub:25
CREATE TABLE auth.authorization_party
(
  id          UUID                                   NOT NULL,
  type        auth.party_type                        NOT NULL,
  resource_id VARCHAR(36)                            NOT NULL,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (type, resource_id)
);
