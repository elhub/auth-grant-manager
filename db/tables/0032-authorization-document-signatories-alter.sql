--changeset elhub:32
ALTER TABLE auth.authorization_document_signatories DROP CONSTRAINT authorization_document_signatories_pkey;
ALTER TABLE auth.authorization_document_signatories DROP COLUMN requested_from;
ALTER TABLE auth.authorization_document_signatories ADD PRIMARY KEY (authorization_document_id);
