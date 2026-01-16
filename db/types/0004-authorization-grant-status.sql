--changeset elhub:4
CREATE TYPE auth.authorization_grant_status AS ENUM (
    'Active',
    'Revoked',
    'Exhausted'
);
