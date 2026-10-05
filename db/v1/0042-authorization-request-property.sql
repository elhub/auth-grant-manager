--changeset elhub:42
CREATE TABLE auth_v1.authorization_request_properties (
    authorization_request_id UUID PRIMARY KEY REFERENCES auth_v1.authorization_request (
        id
    ) ON DELETE CASCADE,
    text_version INT NOT NULL,
    properties JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
