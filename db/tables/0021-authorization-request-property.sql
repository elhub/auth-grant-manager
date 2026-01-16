--changeset elhub:21
CREATE TABLE auth.authorization_request_property
(
  authorization_request_id UUID                                   NOT NULL REFERENCES auth.authorization_request (id) ON DELETE CASCADE,
  key                      VARCHAR(64)                            NOT NULL,
  value                    TEXT                                   NOT NULL,
  created_at               TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  PRIMARY KEY (authorization_request_id, key)
);
