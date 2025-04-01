-- changeset elhub:17
CREATE TABLE auth.authorization_request (
    id UUID PRIMARY KEY,
    type request_type NOT NULL,
    status authorization_request_status NOT NULL,
    requested_by VARCHAR(16) NOT NULL,
    requested_to VARCHAR(16) NOT NULL,
    created_at TIMESTAMP,
    valid_to TIMESTAMP
);
