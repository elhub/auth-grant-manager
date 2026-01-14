--changeset elhub:33
ALTER TABLE auth.authorization_document
    ADD COLUMN valid_to DATE;

UPDATE auth.authorization_document
SET valid_to = CURRENT_DATE
WHERE valid_to IS NULL;

ALTER TABLE auth.authorization_document
    ALTER COLUMN valid_to SET NOT NULL;
