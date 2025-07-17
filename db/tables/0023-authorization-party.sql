--changeset elhub:23
CREATE TABLE auth.authorization_party
(
    id         BIGSERIAL                              NOT NULL,
    type       auth.elhub_resource                    NOT NULL,
    descriptor VARCHAR(32)                            NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (id)
);
