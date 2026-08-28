--changeset elhub:33
ALTER TABLE auth.authorization_grant
DROP CONSTRAINT authorization_grant_source_type_source_id_key;
