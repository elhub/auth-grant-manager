-- changeset elhub:11
CREATE TABLE auth.authorization_scope (
    authorization_grant_id UUID NOT NULL,
    authorized_resource_type authorization_resource NOT NULL,
    authorized_resource_id VARCHAR(255) NOT NULL,
    permission_type permission_type NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
