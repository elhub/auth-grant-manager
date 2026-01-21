INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  'd81e5bf2-8a0c-4348-a788-2a3fab4e77d6',
  'ChangeOfSupplierConfirmation',
  'Pending',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  null,
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47',
  'ChangeOfSupplierConfirmation',
  'Pending',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '66666666-6666-6666-6666-666666666666',
  null,
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
  );

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '4f71d596-99e4-415e-946d-7252c1a40c5b',
  'ChangeOfSupplierConfirmation',
  'Accepted',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '66666666-6666-6666-6666-666666666666',
  '66666666-6666-6666-6666-666666666666',
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '4f71d596-99e4-415e-946d-7252c1a40c51',
  'ChangeOfSupplierConfirmation',
  'Accepted',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '66666666-6666-6666-6666-666666666666',
  '66666666-6666-6666-6666-666666666666',
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '4f71d596-99e4-415e-946d-7252c1a40c52',
  'ChangeOfSupplierConfirmation',
  'Accepted',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '66666666-6666-6666-6666-666666666666',
  '66666666-6666-6666-6666-666666666666',
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '4f71d596-99e4-415e-946d-7252c1a40c50',
  'ChangeOfSupplierConfirmation',
  'Accepted',
  '22222222-2222-2222-2222-222222222222',
  '55555555-5555-5555-5555-555555555555',
  '55555555-5555-5555-5555-555555555555',
  '55555555-5555-5555-5555-555555555555',
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '4f71d596-99e4-415e-946d-7352c1a40c53',
  'ChangeOfSupplierConfirmation',
  'Pending',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  null,
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '5f71d596-99e4-415e-946d-7352c1a40c53',
  'ChangeOfSupplierConfirmation',
  'Accepted',
  '44444444-4444-4444-4444-444444444444',
  '11111111-1111-1111-1111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);

INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedFromName', 'Kasper Lind');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedForMeteringPointId', '1234567890555');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'requestedForMeteringPointAddress', 'Example Street 2, 0654 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'balanceSupplierName', 'Power AS');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('d81e5bf2-8a0c-4348-a788-2a3fab4e77d6', 'balanceSupplierContractName', 'ExampleSupplierContract');

INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7352c1a40c53', 'requestedFromName', 'Ola Normann');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7352c1a40c53', 'requestedForMeteringPointId', '1234567890123');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7352c1a40c53', 'requestedForMeteringPointAddress', 'Example Street 1, 1234 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7352c1a40c53', 'balanceSupplierName', 'Example Energy AS');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7352c1a40c53', 'balanceSupplierContractName', 'ExampleSupplierContract');

INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c50', 'requestedFromName', 'Kari Normann');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c50', 'requestedForMeteringPointId', '1234567890123');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c50', 'requestedForMeteringPointAddress', 'Example Street 1, 1234 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c50', 'balanceSupplierName', 'Example Energy AS');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('4f71d596-99e4-415e-946d-7252c1a40c50', 'balanceSupplierContractName', 'ExampleSupplierContract');

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

INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 'requestedFromName', 'Hans Tobiassen');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 'requestedForMeteringPointId', '666666666666');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 'requestedForMeteringPointAddress', 'Test Street 16, 0674 Oslo');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 'balanceSupplierName', 'Test Energy Group');
INSERT INTO auth.authorization_request_property(authorization_request_id, key, value)
VALUES ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 'balanceSupplierContractName', 'NordicEnergyQ1-2026');
INSERT INTO auth.authorization_request (
  id,
  request_type,
  request_status,
  requested_by,
  requested_from,
  requested_to,
  approved_by,
  created_at,
  updated_at,
  valid_to
)
VALUES (
  '130b6bca-1e3a-4653-8a9b-ccc0dc4fe389',
  'ChangeOfSupplierConfirmation',
  'Pending',
  '22222222-2222-2222-2222-222222222222',
  '11111111-1111-1111-1111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  null,
  '2025-01-01T10:00:00Z',
  '2025-01-01T10:00:00Z',
  '2025-04-04'
);
INSERT INTO auth.authorization_request_scope(
  authorization_request_id,
  authorization_scope_id,
  created_at
)
VALUES
  ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 123, now()),
  ('3f2c9e6b-7a4d-4f1a-9b6e-8c1d2a5e9f47', 345, now()),
  ('4f71d596-99e4-415e-946d-7252c1a40c52', 123, now());
