CREATE TABLE core.terms_of_use (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    version    INTEGER      NOT NULL DEFAULT 1,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_terms_type CHECK (type IN ('MANDATORY', 'NON_MANDATORY'))
);
