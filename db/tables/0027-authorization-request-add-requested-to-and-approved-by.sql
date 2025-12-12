--changeset elhub:27
ALTER TABLE auth.authorization_request
    ADD COLUMN requested_to UUID NOT NULL,
    ADD COLUMN approved_by   UUID;
