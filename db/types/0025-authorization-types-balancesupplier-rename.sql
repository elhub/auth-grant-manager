--changeset elhub:25
ALTER TYPE auth.authorization_permission_type ADD VALUE IF NOT EXISTS 'ChangeOfBalanceSupplierForPerson';
ALTER TYPE auth.authorization_permission_type ADD VALUE IF NOT EXISTS 'MoveInAndChangeOfBalanceSupplierForPerson';

ALTER TYPE auth.authorization_document_type ADD VALUE IF NOT EXISTS 'ChangeOfBalanceSupplierForPerson';
ALTER TYPE auth.authorization_document_type ADD VALUE IF NOT EXISTS 'MoveInAndChangeOfBalanceSupplierForPerson';

ALTER TYPE auth.authorization_request_type ADD VALUE IF NOT EXISTS 'ChangeOfBalanceSupplierForPerson';
ALTER TYPE auth.authorization_request_type ADD VALUE IF NOT EXISTS 'MoveInAndChangeOfBalanceSupplierForPerson';
