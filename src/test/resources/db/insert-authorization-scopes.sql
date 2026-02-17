-- Test data for authorization scopes
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES ('e705af95-571d-47ea-9b1b-742aa598c85c',
    'MeteringPoint',
    'b7f9c2e4',
    'MoveInAndChangeOfEnergySupplierForPerson',
        '2024-04-04 02:00:00.000000+00');
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES ('c597482d-b013-400b-9362-35bb16724c8f',
        'MeteringPoint',
        'b7f9c2e4',
        'ChangeOfEnergySupplierForPerson',
        '2024-04-04 02:00:00.000000+00');
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES ('75ad606f-4ac9-4d4f-acd5-20d6862ec198',
        'Organization',
        'b7f9c2e4',
        'ChangeOfEnergySupplierForPerson',
        '2024-04-04 02:00:00.000000+00');
INSERT INTO auth.authorization_scope (id, authorized_resource_type, authorized_resource_id, permission_type, created_at)
VALUES ('0feefd01-36c7-403b-9bf1-c11d6458f639',
        'Person',
        'b7f9c2e4',
        'MoveInAndChangeOfEnergySupplierForPerson',
        '2024-04-04 02:00:00.000000+00');
