--changeset elhub:8
CREATE TYPE authorization_document_status AS ENUM (
    'Expired',
    'Pending',
    'Rejected',
    'Signed'
);
