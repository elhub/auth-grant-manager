--changeset elhub:10
CREATE TYPE auth.authorization_party_type AS ENUM (
    'Organization',
    'OrganizationEntity',
    'Person'
)
