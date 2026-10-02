--liquibase formatted sql

--changeset elhub:35
CREATE TABLE auth_v1.authorization_request
(
    id UUID PRIMARY KEY,
    request_type auth_v1.AUTHORIZATION_REQUEST_TYPE NOT NULL,
    status auth_v1.AUTHORIZATION_REQUEST_STATUS NOT NULL,
    requested_by UUID NOT NULL REFERENCES auth.authorization_party (id),
    requested_from UUID NOT NULL REFERENCES auth.authorization_party (id),
    requested_to UUID NOT NULL REFERENCES auth.authorization_party (id),
    approved_by UUID REFERENCES auth.authorization_party (id),
    approved_at TIMESTAMPTZ,
    external_reference VARCHAR(255),
    redirect_uri TEXT,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX request_requested_by_created_idx
ON auth_v1.authorization_request (requested_by, created_at DESC, id DESC);

CREATE INDEX request_requested_to_created_idx
ON auth_v1.authorization_request (requested_to, created_at DESC, id DESC);

CREATE TABLE auth_v1.authorization_request_scope
(
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES auth_v1.authorization_request (id),
    resource_type TEXT NOT NULL
);

CREATE INDEX request_scope_request_idx
ON auth_v1.authorization_request_scope (request_id);

CREATE TABLE auth_v1.authorization_request_scope_constraint
(
    id UUID PRIMARY KEY,
    scope_id UUID NOT NULL REFERENCES auth_v1.authorization_request_scope (id),
    kind auth_v1.AUTHORIZATION_CONSTRAINT_KIND NOT NULL,
    attribute TEXT NOT NULL,
    value TEXT[] NOT NULL
);

CREATE INDEX request_scope_constraint_scope_idx
ON auth_v1.authorization_request_scope_constraint (scope_id);
