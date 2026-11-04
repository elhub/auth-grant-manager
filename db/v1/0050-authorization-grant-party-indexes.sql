--liquibase formatted sql
--changeset elhub:50
CREATE INDEX grant_principal_idx ON auth_v1.authorization_grant (principal);

CREATE INDEX grant_granted_to_idx ON auth_v1.authorization_grant (granted_to);
