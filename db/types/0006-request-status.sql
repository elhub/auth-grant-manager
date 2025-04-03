--changeset elhub:6
CREATE TYPE request_status AS ENUM (
    'Accepted',
    'Expired'
    'Pending',
    'Rejected'
);
