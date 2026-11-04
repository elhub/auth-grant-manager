--liquibase formatted sql
--changeset elhub:52
ALTER TYPE auth_v1.authorization_request_type
    ADD VALUE 'EnergySupplierFrameworkAgreement';

ALTER TYPE auth_v1.authorization_document_type
    ADD VALUE 'EnergySupplierFrameworkAgreement';
