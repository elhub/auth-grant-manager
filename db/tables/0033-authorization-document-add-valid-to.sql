--changeset elhub:33
ALTER TABLE auth.authorization_document
    ADD COLUMN valid_to TIMESTAMP WITH TIME ZONE;

UPDATE auth.authorization_document
SET valid_to = CURRENT_TIMESTAMP
WHERE valid_to IS NULL;

ALTER TABLE auth.authorization_document
    ALTER COLUMN valid_to SET NOT NULL;
