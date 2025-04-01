--changeset elhub:10
CREATE TABLE auth.authorization_grant
(
    id                UUID PRIMARY KEY,
    granted_for       VARCHAR(16)                NOT NULL,
    granted_by        VARCHAR(16)                NOT NULL,
    granted_to        VARCHAR(16)                NOT NULL,
    granted_at        TIMESTAMP                  NOT NULL,
    grant_source_type authorization_source       NOT NULL,
    grant_source_id   UUID                       NOT NULL,
    status            authorization_grant_status NOT NULL,
    valid_from        TIMESTAMP WITH TIME ZONE   NOT NULL,
    valid_to          TIMESTAMP WITH TIME ZONE   NOT NULL
);
