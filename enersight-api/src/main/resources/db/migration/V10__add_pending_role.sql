ALTER TABLE core.users
    DROP CONSTRAINT chk_users_role,
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN', 'PENDING'));
