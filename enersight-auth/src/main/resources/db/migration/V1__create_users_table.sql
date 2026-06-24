CREATE TABLE users (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    roles    TEXT[] NOT NULL DEFAULT ARRAY['USER']
);

-- Bootstrap admin for initial login. Dev-only credentials: admin@tecsys.com / Admin@123
-- Rotate this password before any non-local deployment.
-- No "active"/status flag by design: deactivating a user means deleting this row (LGPD erasure),
-- not flipping a flag.
INSERT INTO users (email, password, roles)
VALUES (
    'admin@tecsys.com',
    '$2b$10$qyopWkp9wa2lTCcb18QIXue6GCRjSDGbYyVfnKqiNt95EOj/9jYz2',
    ARRAY['ADMIN']
);
