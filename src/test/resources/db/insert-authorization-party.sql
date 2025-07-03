-- Test data for authorization grant scopes
INSERT INTO auth.authorization_party (id, type, descriptor, name, created_at) VALUES
(1111111111111111, 'ElhubPersonId', '12345678901', 'Ola Nordmann', '2024-06-27 13:00:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, name, created_at) VALUES
(2222222222222222, 'OrganizationNumber', '987654321', 'Testbed AS', '2024-06-27 11:05:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, name, created_at) VALUES
(3333333333333333, 'ElhubPersonId', '23456789012', 'Kari Nordmann', '2024-06-27 11:10:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, name, created_at) VALUES
(4444444444444444, 'OrganizationNumber', '123123123', 'Demo Company', '2024-06-27 11:15:00+00');
INSERT INTO auth.authorization_party (id, type, descriptor, name, created_at) VALUES
(5555555555555555, 'ElhubPersonId', '34567890123', 'Lars Hansen', '2024-06-27 11:20:00+00');
