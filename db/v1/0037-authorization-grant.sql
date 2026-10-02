--liquibase formatted sql
--changeset elhub:37
CREATE TABLE auth_v1.authorization_grant (
    id UUID PRIMARY KEY,
    source_type auth_v1.AUTHORIZATION_GRANT_SOURCE_TYPE NOT NULL,
    source_id UUID NOT NULL,
    granted_for UUID NOT NULL REFERENCES auth.authorization_party (id),
    granted_by UUID NOT NULL REFERENCES auth.authorization_party (id),
    granted_to UUID NOT NULL REFERENCES auth.authorization_party (id),
    status auth_v1.AUTHORIZATION_GRANT_STATUS NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX grant_source_idx ON auth_v1.authorization_grant (
    source_type, source_id
);
