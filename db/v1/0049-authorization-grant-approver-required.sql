--liquibase formatted sql

--changeset elhub:49
ALTER TABLE auth_v1.authorization_grant
    ALTER COLUMN approved_by SET NOT NULL;
