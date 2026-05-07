--changeset elhub:30
-- improves performance when filtering on requested_[by|to] and sorting at the same time
CREATE INDEX ON auth.authorization_request (requested_to, created_at DESC);
CREATE INDEX ON auth.authorization_request (requested_by, created_at DESC);
