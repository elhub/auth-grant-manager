--changeset elhub:4
CREATE TYPE authorization_resource AS ENUM (
    'MeteringPoint',
    'Organization',
    'Person'
);
