-- changeset elhub:9
CREATE TYPE authorization_party_type AS ENUM (
    'ElhubPersonId',
    'ElhubSystem',
    'GlobalLocationNumber',
    'OrganizationNumber'
);
