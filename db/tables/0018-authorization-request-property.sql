-- changeset elhub:18
CREATE TABLE auth.authorization_request_property (
    authorization_request_id UUID        NOT NULL,
    name                     VARCHAR(64) NOT NULL,
    value                    TEXT        NOT NULL
);
