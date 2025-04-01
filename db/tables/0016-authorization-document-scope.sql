-- changeset elhub:16
CREATE TABLE auth.authorization_document_scope (
    authorization_document_id UUID                     NOT NULL,
    authorized_resource_type  authorization_resource   NOT NULL,
    authorized_resource_id    VARCHAR(64)              NOT NULL,
    permission_type           permission_type          NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL
);
