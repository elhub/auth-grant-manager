--liquibase formatted sql

--changeset elhub:43
ALTER TYPE auth_v1.authorization_request_status RENAME TO authorization_request_status_old;

CREATE TYPE auth_v1.authorization_request_status AS ENUM ('Pending', 'Rejected');

ALTER TABLE auth_v1.authorization_request
    ALTER COLUMN status TYPE auth_v1.authorization_request_status
    USING status::text::auth_v1.authorization_request_status;

DROP TYPE auth_v1.authorization_request_status_old;

ALTER TABLE auth_v1.authorization_document DROP COLUMN status;

DROP TYPE auth_v1.authorization_document_status;

CREATE TYPE auth_v1.authorization_resource_type AS ENUM (
    'MeteringPointContract'
);

ALTER TABLE auth_v1.authorization_request_scope
    ALTER COLUMN resource_type TYPE auth_v1.authorization_resource_type
    USING resource_type::auth_v1.authorization_resource_type;

ALTER TABLE auth_v1.authorization_document_scope
    ALTER COLUMN resource_type TYPE auth_v1.authorization_resource_type
    USING resource_type::auth_v1.authorization_resource_type;

ALTER TABLE auth_v1.authorization_grant_scope
    ALTER COLUMN resource_type TYPE auth_v1.authorization_resource_type
    USING resource_type::auth_v1.authorization_resource_type;

CREATE TYPE auth_v1.authorization_constraint_attribute AS ENUM (
    'meteringPoint.id'
);

ALTER TABLE auth_v1.authorization_request_scope_constraint
    ALTER COLUMN attribute TYPE auth_v1.authorization_constraint_attribute
    USING attribute::auth_v1.authorization_constraint_attribute;

ALTER TABLE auth_v1.authorization_document_scope_constraint
    ALTER COLUMN attribute TYPE auth_v1.authorization_constraint_attribute
    USING attribute::auth_v1.authorization_constraint_attribute;

ALTER TABLE auth_v1.authorization_grant_scope_constraint
    ALTER COLUMN attribute TYPE auth_v1.authorization_constraint_attribute
    USING attribute::auth_v1.authorization_constraint_attribute;

ALTER TYPE auth_v1.authorization_constraint_kind
    RENAME VALUE 'AllowedChange' TO 'AllowedChanges';
