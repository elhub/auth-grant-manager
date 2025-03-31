-- changeset elhub:7
CREATE TYPE authorization_document_status AS ENUM (
    'expired'
    'pending',
    'rejected',
    'signed'
);
