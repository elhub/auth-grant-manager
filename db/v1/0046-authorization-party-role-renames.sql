--liquibase formatted sql

--changeset elhub:46
ALTER TABLE auth_v1.authorization_request RENAME COLUMN requested_from TO requested_principal;
ALTER TABLE auth_v1.authorization_request RENAME COLUMN requested_to TO requested_of;

ALTER TABLE auth_v1.authorization_document RENAME COLUMN requested_from TO requested_principal;
ALTER TABLE auth_v1.authorization_document RENAME COLUMN requested_to TO requested_of;
ALTER TABLE auth_v1.authorization_document RENAME COLUMN signed_by TO approved_by;

ALTER TABLE auth_v1.authorization_request
    RENAME CONSTRAINT authorization_request_requested_from_fkey TO authorization_request_requested_principal_fkey;
ALTER TABLE auth_v1.authorization_request
    RENAME CONSTRAINT authorization_request_requested_to_fkey TO authorization_request_requested_of_fkey;
ALTER TABLE auth_v1.authorization_document
    RENAME CONSTRAINT authorization_document_requested_from_fkey TO authorization_document_requested_principal_fkey;
ALTER TABLE auth_v1.authorization_document
    RENAME CONSTRAINT authorization_document_requested_to_fkey TO authorization_document_requested_of_fkey;
ALTER TABLE auth_v1.authorization_document
    RENAME CONSTRAINT authorization_document_signed_by_fkey TO authorization_document_approved_by_fkey;

ALTER INDEX auth_v1.request_requested_to_created_idx RENAME TO request_requested_of_created_idx;
ALTER INDEX auth_v1.document_requested_from_created_idx RENAME TO document_requested_principal_created_idx;
