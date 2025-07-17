-- Test data for authorization grant scopes
INSERT INTO auth.authorization_party (id, type, descriptor, created_at) VALUES
(1111111111111111, 'Person', '12345678901', '2024-06-27 13:00:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, created_at) VALUES
(2222222222222222, 'Organization', '987654321', '2024-06-27 11:05:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, created_at) VALUES
(3333333333333333, 'Person', '23456789012', '2024-06-27 11:10:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, created_at) VALUES
(4444444444444444, 'OrganizationEntity', '123123123', '2024-06-27 11:15:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, created_at) VALUES
(5555555555555555, 'MeteringPoint', '34567890123', '2024-06-27 11:20:00+00');
