--changeset elhub:4
CREATE TYPE permission_type AS ENUM (
    'ChangeOfSuppler',
    'FullDelegation',
    'ReadAccess'
);
