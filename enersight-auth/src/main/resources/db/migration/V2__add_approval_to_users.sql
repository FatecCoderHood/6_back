ALTER TABLE users ADD COLUMN approved BOOLEAN NOT NULL DEFAULT false;

-- The seed admin from V1 is explicitly grandfathered in; everyone else starts unapproved.
UPDATE users SET approved = true WHERE email = 'admin@tecsys.com';
