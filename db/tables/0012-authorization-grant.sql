--changeset elhub:12
CREATE TABLE auth.authorization_grant
(
  id          UUID                                   NOT NULL,
  source_type auth.authorization_grant_source_type   NOT NULL,
  source_id   UUID                                   NOT NULL,
  granted_for UUID                                   NOT NULL,
  granted_by  UUID                                   NOT NULL,
  granted_to  UUID                                   NOT NULL,
  granted_at  TIMESTAMP WITH TIME ZONE               NOT NULL,
  status      auth.authorization_grant_status        NOT NULL,
  valid_from  TIMESTAMP WITH TIME ZONE               NOT NULL,
  valid_to    TIMESTAMP WITH TIME ZONE               NOT NULL,
  created_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (source_type, source_id)
);
