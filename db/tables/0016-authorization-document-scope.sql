-- changeset elhub:16
CREATE TABLE auth.authorization_document_scope
(
    authorization_document_id UUID                     NOT NULL REFERENCES auth.authorization_document (id) ON DELETE CASCADE,
    authorized_resource_type  authorization_resource   NOT NULL,
    authorized_resource_id    VARCHAR(64)              NOT NULL,
    permission_type           permission_type          NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (authorization_document_id, authorized_resource_type, authorized_resource_id, permission_type)
);
