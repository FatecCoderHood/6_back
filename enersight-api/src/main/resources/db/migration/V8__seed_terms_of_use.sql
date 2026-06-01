INSERT INTO core.terms_of_use (id, title, content, type, version, active, created_at)
VALUES
    (
        gen_random_uuid(),
        'Terms of Service',
        'By using EnerSight you agree to our terms of service. You must use the platform responsibly and in accordance with applicable laws. We reserve the right to suspend accounts that violate these terms.',
        'MANDATORY',
        1,
        TRUE,
        NOW()
    ),
    (
        gen_random_uuid(),
        'Marketing Communications',
        'By accepting this term you agree to receive occasional newsletters, product updates and promotional content from EnerSight. You may opt out at any time.',
        'NON_MANDATORY',
        1,
        TRUE,
        NOW()
    );
