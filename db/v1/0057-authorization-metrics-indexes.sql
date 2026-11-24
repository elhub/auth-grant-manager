--liquibase formatted sql

--changeset elhub:57
CREATE INDEX request_type_approver_status_idx
ON auth_v1.authorization_request (request_type, approved_by, status);

CREATE INDEX document_type_approver_idx
ON auth_v1.authorization_document (document_type, approved_by);
