--changeset elhub:6
CREATE TYPE auth.authorization_request_type AS ENUM (
    'ChangeOfEnergySupplierForPerson',
    'MoveInAndChangeOfEnergySupplierForPerson'
)

