--changeset elhub:14
CREATE TABLE auth.authorization_grant_property
(
    authorization_grant_id UUID                                   NOT NULL REFERENCES auth.authorization_grant (id) ON DELETE CASCADE,
    key                    VARCHAR(64)                            NOT NULL,
    value                  TEXT                                   NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    PRIMARY KEY (authorization_grant_id, key)
);


