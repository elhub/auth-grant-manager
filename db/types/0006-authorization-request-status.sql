--changeset elhub:6
CREATE TYPE authorization_request_status AS ENUM (
    'accepted',
    'expired'
    'pending',
    'rejected'
);
