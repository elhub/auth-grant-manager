-- changeset elhub:20
CREATE TABLE auth.authorization_document_property
(
    authorization_document_id UUID                     NOT NULL REFERENCES auth.authorization_document (id) ON DELETE CASCADE,
    key                       VARCHAR(64)              NOT NULL,
    value                     TEXT                     NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (authorization_document_id, key)
);
