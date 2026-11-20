--liquibase formatted sql
--changeset elhub:56
UPDATE auth_v1.authorization_document
SET meta = (meta - 'balanceSupplierName') ||
    CASE WHEN meta ? 'energySupplierName' THEN '{}'::jsonb
         ELSE jsonb_build_object('energySupplierName', meta -> 'balanceSupplierName')
    END
WHERE meta ? 'balanceSupplierName';

UPDATE auth_v1.authorization_request
SET meta = (meta - 'balanceSupplierName') ||
    CASE WHEN meta ? 'energySupplierName' THEN '{}'::jsonb
         ELSE jsonb_build_object('energySupplierName', meta -> 'balanceSupplierName')
    END
WHERE meta ? 'balanceSupplierName';
