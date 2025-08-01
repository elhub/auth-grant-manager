--changeset elhub:18
CREATE TABLE auth.authorization_request
(
    id             UUID                                   NOT NULL,
    request_type   auth.authorization_request_type             NOT NULL,
    request_status auth.authorization_request_status           NOT NULL,
    requested_by   VARCHAR(16)                            NOT NULL,
    requested_from VARCHAR(16)                            NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    valid_to       TIMESTAMP WITH TIME ZONE               NOT NULL,
    PRIMARY KEY (id)
);
