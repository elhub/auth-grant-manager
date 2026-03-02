--changeset elhub:28
ALTER TABLE auth.authorization_request ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE auth.authorization_request ALTER COLUMN updated_at DROP DEFAULT;

ALTER TABLE auth.authorization_document ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE auth.authorization_document ALTER COLUMN updated_at DROP DEFAULT;


ALTER TABLE auth.authorization_grant ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE auth.authorization_grant ALTER COLUMN updated_at DROP DEFAULT;

ALTER TABLE auth.authorization_request_scope DROP COLUMN created_at;
ALTER TABLE auth.authorization_document_scope DROP COLUMN created_at;
ALTER TABLE auth.authorization_grant_scope DROP COLUMN created_at;


ALTER TABLE auth.authorization_scope ALTER COLUMN created_at DROP DEFAULT;

ALTER TABLE auth.authorization_request_property DROP COLUMN created_at;
