--changeset elhub:15
CREATE TABLE auth.authorization_document
(
    id              UUID                                   NOT NULL,
    type            auth.document_type                     NOT NULL,
    file            BYTEA                                  NOT NULL,
    status          auth.authorization_document_status     NOT NULL,
    requested_by    VARCHAR(16)                            NOT NULL,
    requested_from  UUID                                   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE               NOT NULL,
    PRIMARY KEY (id)
);
