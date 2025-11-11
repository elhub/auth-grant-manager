--changeset elhub:24
ALTER TABLE auth.authorization_party
ALTER COLUMN type TYPE auth.party_resource
  USING type::text::auth.party_resource;
