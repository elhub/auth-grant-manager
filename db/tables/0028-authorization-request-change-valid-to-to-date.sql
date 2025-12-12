--changeset elhub:28
ALTER TABLE auth.authorization_request
ALTER COLUMN valid_to
    TYPE DATE
    USING valid_to::DATE;
