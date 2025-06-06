INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_to, valid_to)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6',
        'ChangeOfSupplierConfirmation',
        'Pending',
        '0847976000005',
        '80102512345',
        '2025-04-04 00:00:00+00');
INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_to, valid_to)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b',
        'ChangeOfSupplierConfirmation',
        'Accepted',
        '0847976000005',
        '80102512345',
        '2025-04-04 00:00:00+00');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'contract', 'value1');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'contract', 'value2');
