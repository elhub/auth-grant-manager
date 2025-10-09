--changeset elhub:16
CREATE TABLE auth.authorization_document_signatories
(
    authorization_document_id UUID                     NOT NULL REFERENCES auth.authorization_document (id) ON DELETE CASCADE,
    requested_from            UUID                     NOT NULL,
    signed_by                 VARCHAR(16)              NOT NULL,
    signed_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (authorization_document_id, requested_from)
);
