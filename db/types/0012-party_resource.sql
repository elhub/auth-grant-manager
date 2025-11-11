--changeset elhub:12
CREATE TYPE auth.party_resource AS ENUM (
    'Organization',
    'OrganizationEntity',
    'Person'
)
