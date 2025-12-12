--changeset elhub:30
CREATE TYPE auth.grant_source_type AS ENUM (
    'Document',
    'Request'
);
