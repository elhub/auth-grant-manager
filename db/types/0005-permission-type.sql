--changeset elhub:5
CREATE TYPE permission_type AS ENUM (
    'ChangeOfSupplier',
    'FullDelegation',
    'ReadAccess'
);
