-- changeset elhub:15
CREATE TABLE auth.authorization_document_signatories (
    authorization_document_id UUID,
    requested_to VARCHAR(16) NOT NULL,
    signed_by VARCHAR(16) NOT NULL,
    signed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
