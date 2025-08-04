INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_from, valid_to)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6',
        'ChangeOfSupplierConfirmation',
        'Pending',
        '84797600005',
        '80102512345',
        '2025-04-04 00:00:00+00');
INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_from, valid_to)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b',
        'ChangeOfSupplierConfirmation',
        'Accepted',
        '84797600005',
        '80102512345',
        '2025-04-04 00:00:00+00');

-- Meta properties for first request
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedFromName', 'Ola Normann');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedForMeteringPointId', '1234567890123');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedForMeteringPointAddress', 'Example Street 1, 1234 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'balanceSupplierContractName', 'ExampleSupplierContract');

-- Meta properties for second request
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'requestedFromName', 'Kari Normann');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'requestedForMeteringPointId', '1234567890123');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'requestedForMeteringPointAddress', 'Example Street 1, 1234 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'balanceSupplierContractName', 'ExampleSupplierContract');
