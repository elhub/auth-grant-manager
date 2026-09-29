--liquibase formatted sql

--changeset elhub:36
CREATE TABLE auth_v1.authorization_document
(
    id UUID PRIMARY KEY,
    document_type auth_v1.AUTHORIZATION_DOCUMENT_TYPE NOT NULL,
    status auth_v1.AUTHORIZATION_DOCUMENT_STATUS NOT NULL,
    requested_by UUID NOT NULL REFERENCES auth.authorization_party (id),
    requested_from UUID NOT NULL REFERENCES auth.authorization_party (id),
    requested_to UUID NOT NULL REFERENCES auth.authorization_party (id),
    signed_by UUID REFERENCES auth.authorization_party (id),
    signed_at TIMESTAMPTZ,
    external_reference VARCHAR(255),
    file BYTEA NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX document_requester_created_idx
ON auth_v1.authorization_document (requested_by, created_at DESC, id DESC);

CREATE INDEX document_rights_holder_created_idx
ON auth_v1.authorization_document (requested_from, created_at DESC, id DESC);

CREATE TABLE auth_v1.authorization_document_constraint
(
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES auth_v1.authorization_document (id),
    kind auth_v1.AUTHORIZATION_CONSTRAINT_KIND NOT NULL,
    attribute TEXT NOT NULL,
    value TEXT[] NOT NULL
);

CREATE INDEX document_constraint_document_idx
ON auth_v1.authorization_document_constraint (document_id);
