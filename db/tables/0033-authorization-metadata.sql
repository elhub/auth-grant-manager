--changeset elhub:33
ALTER TABLE auth.authorization_document
  ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD CONSTRAINT authorization_document_metadata_is_object CHECK (jsonb_typeof(metadata) = 'object');

UPDATE auth.authorization_document document
SET metadata = properties.metadata
FROM (
  SELECT authorization_document_id, jsonb_object_agg(key, to_jsonb(value)) AS metadata
  FROM auth.authorization_document_property
  GROUP BY authorization_document_id
) properties
WHERE document.id = properties.authorization_document_id;

DROP TABLE auth.authorization_document_property;

ALTER TABLE auth.authorization_request
  ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD CONSTRAINT authorization_request_metadata_is_object CHECK (jsonb_typeof(metadata) = 'object');

UPDATE auth.authorization_request request
SET metadata = properties.metadata
FROM (
  SELECT authorization_request_id, jsonb_object_agg(key, to_jsonb(value)) AS metadata
  FROM auth.authorization_request_property
  GROUP BY authorization_request_id
) properties
WHERE request.id = properties.authorization_request_id;

DROP TABLE auth.authorization_request_property;
