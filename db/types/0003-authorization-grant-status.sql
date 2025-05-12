--changeset elhub:3
CREATE TYPE authorization_grant_status AS ENUM (
    'Active',
    'Revoked',
    'Expired',
    'Exhausted'
);
