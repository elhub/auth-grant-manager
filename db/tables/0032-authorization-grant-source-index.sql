--changeset elhub:32
CREATE UNIQUE INDEX IF NOT EXISTS authorization_grant_source_idx
    ON auth.authorization_grant (source_type, source_id);
