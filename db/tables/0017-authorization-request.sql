-- changeset elhub:17
CREATE TABLE auth.authorization_request
(
    id           UUID                         NOT NULL,
    type         request_type                 NOT NULL,
    status       authorization_request_status NOT NULL,
    requested_by VARCHAR(16)                  NOT NULL,
    requested_to VARCHAR(16)                  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE     NOT NULL,
    valid_to     TIMESTAMP WITH TIME ZONE     NOT NULL,
    PRIMARY KEY (id)
);
