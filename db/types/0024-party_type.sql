--changeset elhub:24
CREATE TYPE auth.party_type AS ENUM (
    'Organization',
    'OrganizationEntity',
    'Person'
)
