--changeset elhub:31
CREATE INDEX ON auth.authorization_document (requested_by, created_at DESC);
CREATE INDEX ON auth.authorization_document (requested_from, created_at DESC);
