--changeset elhub:11
CREATE TYPE auth.party_type AS ENUM (
    'Organization',
    'OrganizationEntity',
    'Person'
)
