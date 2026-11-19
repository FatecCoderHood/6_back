-- No foreign key on actor/target: audit entries must outlive the users they reference.
CREATE TABLE core.audit_log (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id   UUID,
    action     VARCHAR(50) NOT NULL,
    target_id  UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_target_id ON core.audit_log (target_id);
CREATE INDEX idx_audit_log_created_at ON core.audit_log (created_at);

CREATE TABLE core.data_subject_requests (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    type         VARCHAR(50) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_dsr_user  FOREIGN KEY (user_id) REFERENCES core.users(id),
    CONSTRAINT chk_dsr_type CHECK (type IN ('ANONYMIZATION'))
);
