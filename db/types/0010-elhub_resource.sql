--changeset elhub:10
CREATE TYPE auth.elhub_resource AS ENUM (
    'MeteringPoint',
    'Organization',
    'OrganizationEntity',
    'Person',
    'System'
);
