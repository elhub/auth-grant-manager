--changeset elhub:29
ALTER TABLE auth.authorization_document_signatories ADD CONSTRAINT fk_requested_from FOREIGN KEY (requested_from) REFERENCES auth.authorization_party(id);
ALTER TABLE auth.authorization_document_signatories ADD CONSTRAINT fk_signed_by FOREIGN KEY (signed_by) REFERENCES auth.authorization_party(id);
