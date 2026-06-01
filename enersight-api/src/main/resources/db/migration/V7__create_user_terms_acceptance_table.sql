CREATE TABLE core.user_terms_acceptance (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    term_id    UUID        NOT NULL,
    status     VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_uta_user   FOREIGN KEY (user_id) REFERENCES core.users(id),
    CONSTRAINT fk_uta_term   FOREIGN KEY (term_id) REFERENCES core.terms_of_use(id),
    CONSTRAINT chk_uta_status CHECK (status IN ('ACCEPTED', 'REVOKED'))
);
