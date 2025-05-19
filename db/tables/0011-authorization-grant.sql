--changeset elhub:11
CREATE TABLE auth.authorization_grant
(
    id                UUID                       NOT NULL,
    granted_for       VARCHAR(16)                NOT NULL,
    granted_by        VARCHAR(16)                NOT NULL,
    granted_to        VARCHAR(16)                NOT NULL,
    granted_at        TIMESTAMP WITH TIME ZONE   NOT NULL,
    status            auth.authorization_grant_status NOT NULL,
    valid_from        TIMESTAMP WITH TIME ZONE   NOT NULL,
    valid_to          TIMESTAMP WITH TIME ZONE   NOT NULL,
    PRIMARY KEY (id)
);
