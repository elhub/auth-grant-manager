--changeset elhub:27
ALTER TABLE auth.authorization_request ADD CONSTRAINT fk_approved_by_party FOREIGN KEY (approved_by) REFERENCES auth.authorization_party(id);
ALTER TABLE auth.authorization_request ADD CONSTRAINT fk_requested_by_party FOREIGN KEY (requested_by) REFERENCES auth.authorization_party(id);
ALTER TABLE auth.authorization_request ADD CONSTRAINT fk_requested_from_party FOREIGN KEY (requested_from) REFERENCES auth.authorization_party(id);
ALTER TABLE auth.authorization_request ADD CONSTRAINT fk_requested_to_party FOREIGN KEY (requested_to) REFERENCES auth.authorization_party(id);
