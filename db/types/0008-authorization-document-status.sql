--changeset elhub:8
CREATE TYPE auth.authorization_document_status AS ENUM (
    'Expired',
    'Pending',
    'Rejected',
    'Signed'
);
