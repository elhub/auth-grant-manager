--changeset elhub:23
CREATE TABLE auth.authorization_party
(
    id                UUID                                   NOT NULL,
    type              auth.elhub_resource                    NOT NULL,
    resource_id       VARCHAR(36)                            NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (type, resource_id)
);
