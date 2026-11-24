--liquibase formatted sql

--changeset elhub:57
CREATE INDEX request_type_approved_status_idx
ON auth_v1.authorization_request (request_type, approved_at, status);

CREATE INDEX document_type_signed_idx
ON auth_v1.authorization_document (document_type, signed_at);
