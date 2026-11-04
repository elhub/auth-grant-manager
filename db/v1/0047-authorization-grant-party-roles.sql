--liquibase formatted sql

--changeset elhub:47
ALTER TABLE auth_v1.authorization_grant RENAME COLUMN granted_for TO principal;
ALTER TABLE auth_v1.authorization_grant
    RENAME CONSTRAINT authorization_grant_granted_for_fkey TO authorization_grant_principal_fkey;
ALTER TABLE auth_v1.authorization_grant
    ADD COLUMN approved_by UUID REFERENCES auth.authorization_party (id);
