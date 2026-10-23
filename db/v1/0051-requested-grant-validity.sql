--liquibase formatted sql
--changeset elhub:51
ALTER TABLE auth_v1.authorization_document
    ADD COLUMN requested_grant_valid_from TIMESTAMPTZ,
    ADD COLUMN requested_grant_valid_to TIMESTAMPTZ;

ALTER TABLE auth_v1.authorization_request
    ADD COLUMN requested_grant_valid_from TIMESTAMPTZ,
    ADD COLUMN requested_grant_valid_to TIMESTAMPTZ;
