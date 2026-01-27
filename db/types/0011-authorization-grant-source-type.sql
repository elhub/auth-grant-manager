--changeset elhub:11
CREATE TYPE auth.authorization_grant_source_type AS ENUM (
    'Document',
    'Request'
);
