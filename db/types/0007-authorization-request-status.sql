--changeset elhub:7
CREATE TYPE authorization_request_status AS ENUM (
    'Accepted',
    'Expired',
    'Pending',
    'Rejected'
);
