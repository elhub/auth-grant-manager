--changeset elhub:5
CREATE TYPE auth.authorization_permission_type AS ENUM (
    'ChangeOfEnergySupplierForPerson',
    'MoveInAndChangeOfEnergySupplierForPerson'
);
