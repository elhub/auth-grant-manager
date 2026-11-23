--liquibase formatted sql
--changeset elhub:56
UPDATE auth_v1.authorization_document
SET meta = (meta - 'balanceSupplierName') ||
    CASE
      WHEN jsonb_exists(meta, 'energySupplierName') THEN '{}'::jsonb
      ELSE jsonb_build_object('energySupplierName', meta -> 'balanceSupplierName')
    END
WHERE jsonb_exists(meta, 'balanceSupplierName');

UPDATE auth_v1.authorization_request
SET meta = (meta - 'balanceSupplierName') ||
    CASE
      WHEN jsonb_exists(meta, 'energySupplierName') THEN '{}'::jsonb
      ELSE jsonb_build_object('energySupplierName', meta -> 'balanceSupplierName')
    END
WHERE jsonb_exists(meta, 'balanceSupplierName');
