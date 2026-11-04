--liquibase formatted sql

--changeset elhub:44
ALTER TYPE auth_v1.authorization_constraint_attribute ADD VALUE 'type';
ALTER TYPE auth_v1.authorization_constraint_attribute ADD VALUE 'validFrom';
