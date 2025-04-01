-- changeset elhub:11
CREATE TABLE auth.authorization_scope
(
    authorization_grant_id   UUID                     NOT NULL REFERENCES auth.authorization_grant (id) ON DELETE CASCADE,
    authorized_resource_type authorization_resource   NOT NULL,
    authorized_resource_id   VARCHAR(64)              NOT NULL,
    permission_type          permission_type          NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (authorization_grant_id, authorized_resource_type, authorized_resource_id, permission_type)
);
