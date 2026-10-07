--liquibase formatted sql

--changeset elhub:43
CREATE TYPE auth_v1.authorization_constraint_attribute AS ENUM (
    'MeteringPointId'
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
