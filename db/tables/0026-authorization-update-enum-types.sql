--changeset elhub:26
UPDATE auth.authorization_scope SET permission_type = 'ChangeOfBalanceSupplierForPerson' WHERE permission_type = 'ChangeOfEnergySupplierForPerson';
UPDATE auth.authorization_scope SET permission_type = 'MoveInAndChangeOfBalanceSupplierForPerson' WHERE permission_type = 'MoveInAndChangeOfEnergySupplierForPerson';

UPDATE auth.authorization_document SET type = 'ChangeOfBalanceSupplierForPerson' WHERE type = 'ChangeOfEnergySupplierForPerson';
UPDATE auth.authorization_document SET type = 'MoveInAndChangeOfBalanceSupplierForPerson' WHERE type = 'MoveInAndChangeOfEnergySupplierForPerson';

UPDATE auth.authorization_request SET request_type = 'ChangeOfBalanceSupplierForPerson' WHERE request_type = 'ChangeOfEnergySupplierForPerson';
UPDATE auth.authorization_request SET request_type = 'MoveInAndChangeOfBalanceSupplierForPerson' WHERE request_type = 'MoveInAndChangeOfEnergySupplierForPerson';
