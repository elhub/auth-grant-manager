--liquibase formatted sql
--changeset elhub:54
ALTER TABLE auth_v1.authorization_request
    ADD COLUMN text_language TEXT CHECK (text_language IN ('nb', 'nn', 'en'));
