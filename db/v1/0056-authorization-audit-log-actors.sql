--liquibase formatted sql
--changeset elhub:56
ALTER TABLE auth_v1.authorization_audit_log
    RENAME COLUMN changed_by TO changed_by_principal;

ALTER TABLE auth_v1.authorization_audit_log
    ADD COLUMN changed_by_actor VARCHAR(64) NOT NULL;
