-- Test data for authorization grant scopes
INSERT INTO auth.authorization_grant_scope (authorization_grant_id, authorization_scope_id, created_at)
VALUES ('123e4567-e89b-12d3-a456-426614174000',
        123,
        '2024-04-04 02:00:00+00');
INSERT INTO auth.authorization_grant_scope (authorization_grant_id, authorization_scope_id, created_at)
VALUES ('b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a',
        345,
        '2023-02-04 15:00:00+00');
INSERT INTO auth.authorization_grant_scope (authorization_grant_id, authorization_scope_id, created_at)
VALUES ('b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a',
        567,
        '2020-02-02 15:00:00+00');
INSERT INTO auth.authorization_grant_scope (authorization_grant_id, authorization_scope_id, created_at)
VALUES ('b7f9c2e4-5a3d-4e2b-9c1a-8f6e2d3c4b5a',
        678,
        '2019-08-03 20:00:00+00');
