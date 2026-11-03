--liquibase formatted sql
--changeset elhub:55
ALTER TABLE auth_v1.authorization_request
    ADD COLUMN text_reference TEXT NOT NULL DEFAULT 'v1';

ALTER TABLE auth_v1.authorization_request
    ALTER COLUMN text_reference DROP DEFAULT;
