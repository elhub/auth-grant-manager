--changeset elhub:24
ALTER TABLE auth.authorization_party
ALTER COLUMN type TYPE auth.party_type
  USING type::text::auth.party_type;
