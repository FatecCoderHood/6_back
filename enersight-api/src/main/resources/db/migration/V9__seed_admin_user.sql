-- password: 1234
INSERT INTO core.users (id, name, email, password, role, active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'Admin',
    'admin@admin.com',
    '$2b$10$xeUE285o47nxldPUgAhBGuMCXcX6WoMmNppRVhu40pd29u0Qitr42',
    'ADMIN',
    TRUE,
    NOW(),
    NOW()
);
