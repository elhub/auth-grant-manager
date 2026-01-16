--changeset elhub:6
CREATE TYPE auth.permission_type AS ENUM (
    'ChangeOfEnergySupplierForPerson',
    'MoveInAndChangeOfEnergySupplierForPerson'
);
