CREATE ROLE flyway_user LOGIN PASSWORD 'test-flyway';
CREATE ROLE app_user LOGIN PASSWORD 'test-app';
ALTER DATABASE enersight_test OWNER TO flyway_user;
ALTER SCHEMA public OWNER TO flyway_user;
GRANT USAGE ON SCHEMA public TO app_user;
CREATE EXTENSION IF NOT EXISTS postgis;
