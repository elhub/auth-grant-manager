--changeset elhub:8
CREATE TYPE auth.authorization_document_status AS ENUM (
    'Pending',
    'Rejected'
);
