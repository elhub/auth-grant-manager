--changeset elhub:3
CREATE TYPE authorization_source AS ENUM (
    'AuthorizationRequest',
    'AuthorizationDocument'
);
