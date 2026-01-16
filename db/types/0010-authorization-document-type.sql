--changeset elhub:10
CREATE TYPE auth.document_type AS ENUM (
    'ChangeOfEnergySupplierForPerson',
    'MoveInAndChangeOfEnergySupplierForPerson'
);
