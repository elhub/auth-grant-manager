--changeset elhub:7
CREATE TYPE auth.authorization_request_type AS ENUM (
    'ChangeOfEnergySupplierForPerson',
    'MoveInAndChangeOfEnergySupplierForPerson'
)

