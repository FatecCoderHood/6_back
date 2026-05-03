CREATE TYPE ingestion_status_enum AS ENUM (
    'pending',
    'processing',
    'done',
    'failed'
);

CREATE TABLE staging.ingestion_jobs (
    job_id UUID PRIMARY KEY,

    org_id VARCHAR(16),
    title VARCHAR(255),
    description TEXT,

    url TEXT,

    ingestion_status ingestion_status_enum NOT NULL DEFAULT 'pending',

    attempts INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    CONSTRAINT uq_ingestion_url UNIQUE (url),

    CONSTRAINT chk_ingestion_status CHECK (
        ingestion_status IN ('pending', 'processing', 'done', 'failed')
    )
);