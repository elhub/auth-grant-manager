-- Test data for authorization scopes
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES (123,
    'MeteringPoint',
    'b7f9c2e4',
    'ReadAccess',
    '2024-04-04 02:00:00+00');
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES (345,
        'MeteringPoint',
        'b7f9c2e4',
        'ChangeOfSupplier',
        '2024-04-04 02:00:00+00');
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES (567,
        'Organization',
        'b7f9c2e4',
        'ChangeOfSupplier',
        '2024-04-04 02:00:00+00');
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES (678,
        'Person',
        'b7f9c2e4',
        'FullDelegation',
        '2024-04-04 02:00:00+00');
