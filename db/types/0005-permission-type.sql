--changeset elhub:5
CREATE TYPE auth.permission_type AS ENUM (
    'ChangeOfSupplier',
    'FullDelegation',
    'ReadAccess'
);
