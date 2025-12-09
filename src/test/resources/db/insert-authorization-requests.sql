INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_from, requested_to, approved_by, valid_to)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6',
        'ChangeOfSupplierConfirmation',
        'Pending',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        '66666666-6666-6666-6666-666666666666',
        null,
        '2025-04-04 00:00:00+00');

INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_from, requested_to, approved_by,valid_to)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b',
        'ChangeOfSupplierConfirmation',
        'Accepted',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        '66666666-6666-6666-6666-666666666666',
        '66666666-6666-6666-6666-666666666666',
        '2025-04-04 00:00:00+00');

INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_from, requested_to, approved_by, valid_to)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c51',
        'ChangeOfSupplierConfirmation',
        'Accepted',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        '66666666-6666-6666-6666-666666666666',
        '66666666-6666-6666-6666-666666666666',
        '2025-04-04 00:00:00+00');

INSERT INTO auth.authorization_request (id, request_type, request_status, requested_by, requested_from, requested_to, approved_by, valid_to)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c52',
        'ChangeOfSupplierConfirmation',
        'Accepted',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        '66666666-6666-6666-6666-666666666666',
        '66666666-6666-6666-6666-666666666666',
        '2025-04-04 00:00:00+00');

-- Meta properties for first request
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedFromName', 'Ola Normann');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedForMeteringPointId', '1234567890123');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedForMeteringPointAddress', 'Example Street 1, 1234 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'balanceSupplierName', 'Example Energy AS');
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
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'balanceSupplierName', 'Example Energy AS');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c5b', 'balanceSupplierContractName', 'ExampleSupplierContract');

-- Meta properties for third request
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c51', 'requestedFromName', 'Per Hansen');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c51', 'requestedForMeteringPointId', '9876543210987');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c51', 'requestedForMeteringPointAddress', 'Main Street 42, 5000 Bergen');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c51', 'balanceSupplierName', 'Green Power Ltd');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c51', 'balanceSupplierContractName', 'GreenPowerContract2025');

-- Meta properties for fourth request
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c52', 'requestedFromName', 'Anna Johansen');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c52', 'requestedForMeteringPointId', '5555555555555');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c52', 'requestedForMeteringPointAddress', 'Sunset Avenue 10, 0150 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c52', 'balanceSupplierName', 'Nordic Energy Group');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c52', 'balanceSupplierContractName', 'NordicEnergyQ1-2025');
