--changeset elhub:24
CREATE TABLE auth.authorization_party
(
  id         UUID                                   NOT NULL,
  type       auth.authorization_party_type          NOT NULL,
  party_id   VARCHAR(64)                            NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (type, party_id)
);
