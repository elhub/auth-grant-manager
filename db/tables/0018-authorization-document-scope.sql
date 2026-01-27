--changeset elhub:18
CREATE TABLE auth.authorization_document_scope
(
  authorization_document_id UUID                                   NOT NULL REFERENCES auth.authorization_document (id) ON DELETE CASCADE,
  authorization_scope_id    UUID                                   NOT NULL REFERENCES auth.authorization_scope (id) ON DELETE CASCADE,
  created_at                TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
  PRIMARY KEY (authorization_document_id, authorization_scope_id)
);
