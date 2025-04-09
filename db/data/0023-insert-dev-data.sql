--changeset elhub:23
-- Test data for authorization requests
INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_to, valid_to)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6',
        'ChangeOfSupplierConfirmation',
        'Pending',
        '0847976000005',
        '80102512345',
        '2025-04-04 00:00:00+00');
