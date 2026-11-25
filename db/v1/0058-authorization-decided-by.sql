--liquibase formatted sql

--changeset elhub:58
ALTER TABLE auth_v1.authorization_request RENAME COLUMN approved_by TO decided_by;
ALTER TABLE auth_v1.authorization_document RENAME COLUMN approved_by TO decided_by;

ALTER TABLE auth_v1.authorization_request
    RENAME CONSTRAINT authorization_request_approved_by_fkey TO authorization_request_decided_by_fkey;
ALTER TABLE auth_v1.authorization_document
    RENAME CONSTRAINT authorization_document_approved_by_fkey TO authorization_document_decided_by_fkey;
