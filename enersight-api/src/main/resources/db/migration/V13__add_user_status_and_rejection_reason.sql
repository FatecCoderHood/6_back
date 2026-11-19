-- Lifecycle moves from role to status; role keeps only what a user may do.
ALTER TABLE core.users
    ADD COLUMN status           VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN rejection_reason TEXT;

UPDATE core.users SET status = 'PENDING', role = 'USER' WHERE role = 'PENDING';

ALTER TABLE core.users
    DROP CONSTRAINT chk_users_role,
    ADD CONSTRAINT chk_users_role   CHECK (role IN ('USER', 'ADMIN')),
    ADD CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'ANONYMIZED')),
    ADD CONSTRAINT chk_users_rejection_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL);
