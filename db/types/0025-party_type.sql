--changeset elhub:25
CREATE TYPE auth.party_type AS ENUM (
    'Organization',
    'OrganizationEntity',
    'Person'
)
