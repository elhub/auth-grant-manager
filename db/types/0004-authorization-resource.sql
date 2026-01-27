--changeset elhub:4
CREATE TYPE auth.authorization_resource AS ENUM (
    'MeteringPoint',
    'Organization',
    'OrganizationEntity',
    'Person',
    'System'
);
