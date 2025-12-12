--changeset elhub:26
ALTER TABLE auth.authorization_document
    ADD COLUMN requested_to UUID NOT NULL,
    ADD COLUMN signed_by   UUID NOT NULL;
