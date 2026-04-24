-- =========================================
-- 0. Safety settings
-- =========================================
\set ON_ERROR_STOP on

-- =========================================
-- 1. Create roles (idempotent)
-- =========================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM pg_roles WHERE rolname = 'flyway_user'
    ) THEN
        CREATE ROLE flyway_user LOGIN PASSWORD 'flyway_password';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM pg_roles WHERE rolname = 'app_user'
    ) THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_password';
    END IF;
END
$$;


-- =========================================
-- 2. Create database (idempotent workaround)
-- =========================================
\c postgres;
CREATE EXTENSION IF NOT EXISTS dblink;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM pg_database WHERE datname = 'enersight_app'
    ) THEN
        PERFORM dblink_exec('dbname=postgres',
            'CREATE DATABASE enersight_app OWNER flyway_user');
    END IF;
END
$$;


-- =========================================
-- 3. Connect to target DB
-- =========================================

\c enersight_app;


-- =========================================
-- 4. Schema ownership (safe)
-- =========================================

-- Ensure public schema exists and is owned correctly
ALTER SCHEMA public OWNER TO flyway_user;

-- Ensure app user can use schema
GRANT USAGE ON SCHEMA public TO app_user;

-- Prevent schema creation by app user
REVOKE CREATE ON SCHEMA public FROM app_user;


-- =========================================
-- 5. Existing object permissions
-- =========================================

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA public
TO app_user;

GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA public
TO app_user;


-- =========================================
-- 6. Default privileges (future objects)
-- =========================================

ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA public
GRANT USAGE, SELECT ON SEQUENCES TO app_user;


-- =========================================
-- 7. Optional hardening
-- =========================================

-- Prevent public access (optional but recommended)
REVOKE ALL ON DATABASE enersight_app FROM PUBLIC;

-- Allow only needed users
GRANT CONNECT ON DATABASE enersight_app TO flyway_user;
GRANT CONNECT ON DATABASE enersight_app TO app_user;


-- =========================================
-- 8. Visibility check (debug/logging)
-- =========================================

DO $$
BEGIN
    RAISE NOTICE 'Roles and database initialized successfully';
END
$$;