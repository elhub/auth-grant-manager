--changeset elhub:31
ALTER TABLE auth.authorization_grant
    ADD COLUMN source_type auth.grant_source_type NOT NULL,
    ADD COLUMN source_id UUID NOT NULL;
