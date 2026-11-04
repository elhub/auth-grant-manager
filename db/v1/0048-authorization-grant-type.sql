--liquibase formatted sql

--changeset elhub:48
CREATE TYPE auth_v1.authorization_grant_type AS ENUM ('Persistent', 'Consumable');

ALTER TABLE auth_v1.authorization_grant
    ADD COLUMN type auth_v1.authorization_grant_type NOT NULL;
