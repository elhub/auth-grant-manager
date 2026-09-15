--changeset elhub:33
ALTER TABLE auth.authorization_request_property
    ALTER COLUMN value TYPE JSONB USING to_jsonb(value);

ALTER TABLE auth.authorization_document_property
    ALTER COLUMN value TYPE JSONB USING to_jsonb(value);

ALTER TABLE auth.authorization_grant_property
    ALTER COLUMN value TYPE JSONB USING to_jsonb(value);
