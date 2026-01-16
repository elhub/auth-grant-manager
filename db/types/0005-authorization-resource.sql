--changeset elhub:5
CREATE TYPE auth.authorization_resource AS ENUM (
    'MeteringPoint',
    'Organization',
    'OrganizationEntity',
    'Person',
    'System'
);
