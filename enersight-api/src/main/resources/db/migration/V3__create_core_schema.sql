-- =================================================
-- 1. Core schema creation and permission setup
-- =================================================

CREATE SCHEMA IF NOT EXISTS core AUTHORIZATION flyway_user;

ALTER SCHEMA core OWNER TO flyway_user;

GRANT USAGE ON SCHEMA core TO app_user;
REVOKE CREATE ON SCHEMA core FROM app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA core
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA core
GRANT USAGE, SELECT ON SEQUENCES TO app_user;

DO $$
BEGIN
    RAISE NOTICE 'Core schema created and permissions set';
END
$$;

-- =================================================
-- 2. Staging schema creation and permission setup
-- =================================================

CREATE SCHEMA IF NOT EXISTS staging AUTHORIZATION flyway_user;

ALTER SCHEMA staging OWNER TO flyway_user;

GRANT USAGE ON SCHEMA staging TO app_user;
REVOKE CREATE ON SCHEMA staging FROM app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA staging
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE flyway_user IN SCHEMA staging
GRANT USAGE, SELECT ON SEQUENCES TO app_user;

DO $$
BEGIN
    RAISE NOTICE 'Staging schema created and permissions set';
END
$$;
