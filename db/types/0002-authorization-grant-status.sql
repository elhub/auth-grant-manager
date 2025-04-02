--changeset elhub:2
CREATE TYPE authorization_grant_status AS ENUM (
    'Active',
    'Revoked',
    'Expired',
    'Exhausted'
);
