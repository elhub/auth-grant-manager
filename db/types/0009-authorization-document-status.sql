--changeset elhub:9
CREATE TYPE auth.authorization_document_status AS ENUM (
    'Pending',
    'Rejected'
);
