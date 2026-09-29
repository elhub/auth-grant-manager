--liquibase formatted sql
--changeset elhub:34
CREATE TYPE auth_v1.authorization_request_type AS ENUM (
    'ChangeOfEnergySupplierForOrganization',
    'MoveInAndChangeOfEnergySupplierForOrganization'
);

CREATE TYPE auth_v1.authorization_document_type AS ENUM (
    'ChangeOfEnergySupplierForOrganization',
    'MoveInAndChangeOfEnergySupplierForOrganization'
);

-- Expired is derived from status and valid_to, not persisted.
CREATE TYPE auth_v1.authorization_request_status AS ENUM (
    'Pending', 'Accepted', 'Rejected'
);

CREATE TYPE auth_v1.authorization_document_status AS ENUM (
    'Pending', 'Signed', 'Rejected'
);

CREATE TYPE auth_v1.authorization_grant_status AS ENUM (
    'Active', 'Exhausted', 'Revoked'
);

CREATE TYPE auth_v1.authorization_grant_source_type AS ENUM (
    'Document', 'Request'
);

CREATE TYPE auth_v1.authorization_capability AS ENUM ('Read', 'Write');

CREATE TYPE auth_v1.authorization_constraint_kind AS ENUM (
    'AppliesTo', 'AllowedChange'
);
