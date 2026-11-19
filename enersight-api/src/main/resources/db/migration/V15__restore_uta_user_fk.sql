-- V12 dropped this FK so users could be hard-deleted. Users are now anonymized, never deleted,
-- so acceptances always keep a valid owner. Acceptances of already-deleted users are orphans
-- (no owner left to attribute them to) and are removed so the constraint can be restored.
DELETE FROM core.user_terms_acceptance
WHERE user_id NOT IN (SELECT id FROM core.users);

ALTER TABLE core.user_terms_acceptance
    ADD CONSTRAINT fk_uta_user FOREIGN KEY (user_id) REFERENCES core.users(id);
