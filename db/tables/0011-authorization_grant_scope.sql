-- changeset elhub:11
CREATE TABLE auth.authorization_grant_scope
(
    authorization_grant_id UUID                     NOT NULL REFERENCES auth.authorization_grant (id) ON DELETE CASCADE,
    authorization_scope_id BIGINT                   NOT NULL REFERENCES auth.authorization_scope (id) ON DELETE CASCADE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (authorization_grant_id, authorization_scope_id)
);
