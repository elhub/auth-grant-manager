-- Test data for authorization grants
INSERT INTO auth.authorization_grant (id, granted_for, granted_by, granted_to, granted_at, status, valid_from, valid_to,
                                      source_type, source_id)
VALUES ('123e4567-e89b-12d3-a456-426614174000',
        '11111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111',
        '55555555-5555-5555-5555-555555555555',
        '2025-04-04 02:00:00+00',
        'Active',
        '2025-04-04 02:00:00+00',
        '2026-04-04 02:00:00+00',
        'Request',
        '4f71d596-99e4-415e-946d-7252c1a40c5b');

INSERT INTO auth.authorization_grant (id, granted_for, granted_by, granted_to, granted_at, status, valid_from, valid_to,
                                      source_type, source_id)
VALUES ('b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a',
        '33333333-3333-3333-3333-333333333333',
        '33333333-3333-3333-3333-333333333333',
        '22222222-2222-2222-2222-222222222222',
        '2023-04-04 02:00:00+00',
        'Expired',
        '2023-04-04 02:00:00+00',
        '2024-04-04 02:00:00+00',
        'Request',
        '4f71d596-99e4-415e-946d-7252c1a40c51');

INSERT INTO auth.authorization_grant (id, granted_for, granted_by, granted_to, granted_at, status, valid_from, valid_to,
                                      source_type, source_id)
VALUES ('a8f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a',
        '44444444-4444-4444-4444-444444444444',
        '33333333-3333-3333-3333-333333333333',
        '55555555-5555-5555-5555-555555555555',
        '2025-01-04 02:00:00+00',
        'Revoked',
        '2025-02-03 16:07:00+00',
        '2025-05-16 02:00:00+00',
        'Request',
        '4f71d596-99e4-415e-946d-7252c1a40c52');


INSERT INTO auth.authorization_grant (id, granted_for, granted_by, granted_to, granted_at, status, valid_from, valid_to,
                                      source_type, source_id)
VALUES ('d75522ba-0e62-449b-b1de-70b16f12ecaf',
        '33333333-3333-3333-3333-333333333333',
        '33333333-3333-3333-3333-333333333333',
        '55555555-5555-5555-5555-555555555555',
        '2023-04-04 02:00:00+00',
        'Expired',
        '2023-04-04 02:00:00+00',
        '2024-04-04 02:00:00+00',
        'Request',
        '8150d80b-3a48-401e-a6d5-025bd3aa1254');
