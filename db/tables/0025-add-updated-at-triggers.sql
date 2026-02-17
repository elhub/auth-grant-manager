--changeset elhub:25

CREATE OR REPLACE FUNCTION auth.set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- authorization_request
DROP TRIGGER IF EXISTS trg_authorization_request_set_updated_at
  ON auth.authorization_request;
CREATE TRIGGER trg_authorization_request_set_updated_at
  BEFORE UPDATE ON auth.authorization_request
  FOR EACH ROW
  EXECUTE FUNCTION auth.set_updated_at();

-- authorization_document
DROP TRIGGER IF EXISTS trg_authorization_document_set_updated_at
  ON auth.authorization_document;
CREATE TRIGGER trg_authorization_document_set_updated_at
  BEFORE UPDATE ON auth.authorization_document
  FOR EACH ROW
  EXECUTE FUNCTION auth.set_updated_at();

-- authorization_grant
DROP TRIGGER IF EXISTS trg_authorization_grant_set_updated_at
  ON auth.authorization_grant;
CREATE TRIGGER trg_authorization_grant_set_updated_at
  BEFORE UPDATE ON auth.authorization_grant
  FOR EACH ROW
  EXECUTE FUNCTION auth.set_updated_at();
