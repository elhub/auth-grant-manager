--changeset elhub:42
CREATE TABLE auth_v1.authorization_request_property (
    authorization_request_id UUID NOT NULL REFERENCES auth_v1.authorization_request (
        id
    ) ON DELETE CASCADE,
    key VARCHAR(64) NOT NULL,
    value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (authorization_request_id, key)
);
