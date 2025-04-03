-- changeset elhub:16
CREATE TABLE auth.authorization_document_scope
(
    authorization_document_id UUID                     NOT NULL REFERENCES auth.authorization_document (id) ON DELETE CASCADE,
    authorization_scope_id    BIGINT                   NOT NULL REFERENCES auth.authorization_scope (id) ON DELETE CASCADE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (authorization_document_id, authorization_scope_id)
);
