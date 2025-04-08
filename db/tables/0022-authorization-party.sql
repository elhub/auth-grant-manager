-- changeset elhub:22
CREATE TABLE auth.authorization_party
(
    id         BIGSERIAL                              NOT NULL,
    type       authorization_party_type               NOT NULL,
    descriptor VARCHAR(32)                            NOT NULL,
    name       VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (id)
);
