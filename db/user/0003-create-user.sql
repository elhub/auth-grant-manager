--liquibase formatted sql

--changeset elhub:3
CREATE USER ${APP_USERNAME} WITH PASSWORD '${APP_PASSWORD}';

-- Grant access to existing tables in schema 'auth'
GRANT USAGE ON SCHEMA auth TO ${APP_USERNAME};
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA auth TO ${APP_USERNAME};

-- Grant access to future tables in schema 'auth'
ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT SELECT, INSERT, UPDATE ON TABLES TO ${APP_USERNAME};

-- Grant access to existing sequences in schema 'auth'
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA auth TO ${APP_USERNAME};

-- Grant access to future sequences in schema 'auth'
ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ${APP_USERNAME};
