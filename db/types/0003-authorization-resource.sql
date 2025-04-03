--changeset elhub:3
CREATE TYPE authorization_resource AS ENUM (
    'MeteringPoint',
    'Organization',
    'Person'
);
