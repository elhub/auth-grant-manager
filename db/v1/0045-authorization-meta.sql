--liquibase formatted sql
--changeset elhub:45
ALTER TABLE auth_v1.authorization_request
ADD COLUMN meta JSONB NOT NULL;

ALTER TABLE auth_v1.authorization_document
ADD COLUMN meta JSONB NOT NULL;

DROP TABLE auth_v1.authorization_request_properties;
