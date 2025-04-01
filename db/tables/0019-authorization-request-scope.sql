-- changeset elhub:19
CREATE TABLE auth.authorization_request_scope (
    authorization_request_id UUID                     NOT NULL,
    authorized_resource_type authorization_resource   NOT NULL,
    authorized_resource_id   VARCHAR(64)              NOT NULL,
    permission_type          permission_type          NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL
);
