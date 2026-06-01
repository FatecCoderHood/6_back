WITH admin_user AS (
    SELECT id FROM core.users WHERE email = 'admin@admin.com'
),
terms AS (
    SELECT id FROM core.terms_of_use WHERE active = TRUE
)
INSERT INTO core.user_terms_acceptance (user_id, term_id, status, created_at)
SELECT admin_user.id, terms.id, 'ACCEPTED', NOW()
FROM admin_user
CROSS JOIN terms;
