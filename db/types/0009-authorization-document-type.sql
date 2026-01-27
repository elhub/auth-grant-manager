--changeset elhub:9
CREATE TYPE auth.authorization_document_type AS ENUM (
    'ChangeOfEnergySupplierForPerson',
    'MoveInAndChangeOfEnergySupplierForPerson'
);
