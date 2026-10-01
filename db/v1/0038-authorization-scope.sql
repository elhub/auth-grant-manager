--liquibase formatted sql

--changeset elhub:38
CREATE TABLE auth_v1.authorization_grant_scope
(
    id UUID PRIMARY KEY,
    grant_id UUID NOT NULL REFERENCES auth_v1.authorization_grant (id),
    capability auth_v1.AUTHORIZATION_CAPABILITY NOT NULL,
    resource_type TEXT NOT NULL
);

CREATE INDEX grant_scope_grant_idx
ON auth_v1.authorization_grant_scope (grant_id);

CREATE INDEX grant_scope_permission_idx
ON auth_v1.authorization_grant_scope (resource_type, capability);

CREATE TABLE auth_v1.authorization_grant_scope_constraint
(
    id UUID PRIMARY KEY,
    scope_id UUID NOT NULL REFERENCES auth_v1.authorization_grant_scope (id),
    kind auth_v1.AUTHORIZATION_CONSTRAINT_KIND NOT NULL,
    resource_type TEXT NOT NULL,
    attribute TEXT NOT NULL,
    value TEXT[] NOT NULL
);

CREATE INDEX grant_scope_constraint_scope_idx
ON auth_v1.authorization_grant_scope_constraint (scope_id);

CREATE INDEX grant_scope_constraint_lookup_idx
ON auth_v1.authorization_grant_scope_constraint (
    kind, resource_type, attribute
);

CREATE INDEX grant_scope_constraint_value_idx
ON auth_v1.authorization_grant_scope_constraint USING gin (value);
