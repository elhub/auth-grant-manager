--liquibase formatted sql
--changeset elhub:53
ALTER TABLE auth_v1.authorization_grant
    ALTER COLUMN valid_to DROP NOT NULL;
