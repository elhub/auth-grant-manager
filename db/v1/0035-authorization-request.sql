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
    language auth_v1.AUTHORIZATION_LANGUAGE NOT NULL,
    redirect_uri TEXT,
    valid_to TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX request_requester_created_idx
ON auth_v1.authorization_request (requested_by, created_at DESC, id DESC);

CREATE INDEX request_approver_created_idx
ON auth_v1.authorization_request (requested_to, created_at DESC, id DESC);

CREATE TABLE auth_v1.authorization_request_constraint
(
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES auth_v1.authorization_request (id),
    kind auth_v1.AUTHORIZATION_CONSTRAINT_KIND NOT NULL,
    attribute TEXT NOT NULL,
    value TEXT[] NOT NULL
);

CREATE INDEX request_constraint_request_idx
ON auth_v1.authorization_request_constraint (request_id);
