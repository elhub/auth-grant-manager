--changeset elhub:3
CREATE TYPE auth.authorization_grant_status AS ENUM (
    'Active',
    'Revoked',
    'Expired',
    'Exhausted'
);
