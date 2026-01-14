--changeset elhub:31
ALTER TYPE auth.authorization_request_type ADD VALUE IF NOT EXISTS 'MoveIn';
ALTER TYPE auth.document_type ADD VALUE IF NOT EXISTS 'MoveIn';
ALTER TYPE auth.permission_type ADD VALUE IF NOT EXISTS 'MoveIn';
