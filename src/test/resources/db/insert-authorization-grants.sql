-- Test data for authorization grants
INSERT INTO auth.authorization_grant (id, granted_for, granted_by, granted_to, granted_at, status, valid_from, valid_to)
VALUES('123e4567-e89b-12d3-a456-426614174000',
       '1111111111111111',
       '1111111111111111',
       '2222222222222222',
       '2025-04-04 02:00:00+00',
       'Active',
       '2025-04-04 02:00:00+00',
       '2026-04-04 02:00:00+00');
INSERT INTO auth.authorization_grant (id, granted_for, granted_by, granted_to, granted_at, status, valid_from, valid_to)
VALUES('b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a',
    '3333333333333333',
    '3333333333333333',
    '2222222222222222',
    '2023-04-04 02:00:00+00',
    'Expired',
    '2023-04-04 02:00:00+00',
    '2024-04-04 02:00:00+00');

