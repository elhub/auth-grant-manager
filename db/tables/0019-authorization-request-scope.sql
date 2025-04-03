-- changeset elhub:19
CREATE TABLE auth.authorization_request_scope
(
    authorization_request_id UUID                                   NOT NULL REFERENCES auth.authorization_request (id) ON DELETE CASCADE,
    authorization_scope_id   BIGINT                                 NOT NULL REFERENCES auth.authorization_scope (id) ON DELETE CASCADE,
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (authorization_request_id, authorization_scope_id)
);
