-- changeset elhub:11
CREATE TABLE auth.authorization_scope
(
    id                       BIGSERIAL                              NOT NULL,
    authorized_resource_type authorization_resource                 NOT NULL,
    authorized_resource_id   VARCHAR(64)                            NOT NULL,
    permission_type          permission_type                        NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (id)
);
