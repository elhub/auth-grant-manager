-- changeset elhub:14
CREATE TABLE auth.authorization_document
(
    id           UUID                          NOT NULL,
    title        VARCHAR(255)                  NOT NULL,
    type         document_type                 NOT NULL,
    file         BYTEA                         NOT NULL,
    status       authorization_document_status NOT NULL,
    requested_by VARCHAR(16)                   NOT NULL,
    requested_to VARCHAR(16)                   NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE      NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE      NOT NULL,
    PRIMARY KEY (id)
);
