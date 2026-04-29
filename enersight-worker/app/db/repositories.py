from sqlalchemy import select
from app.db.session import SessionLocal
from app.models.job import IngestionJob
from app.enums.ingestion_status import IngestionStatus


def fetch_and_lock_job():
    with SessionLocal() as session:
        stmt = (
            select(IngestionJob)
            .where(IngestionJob.ingestion_status == IngestionStatus.PENDING)
            .with_for_update(skip_locked=True)
            .limit(1)
        )

        job = session.execute(stmt).scalar_one_or_none()

        if job:
            job.ingestion_status = IngestionStatus.PROCESSING
            session.commit()

        return job

def mark_job_done(job_id):
    with SessionLocal() as session:
        job = session.get(IngestionJob, job_id)
        if job:
            job.ingestion_status = IngestionStatus.DONE
            session.commit()

def mark_job_failed(job_id):
    with SessionLocal() as session:
        job = session.get(IngestionJob, job_id)
        if job:
            job.ingestion_status = IngestionStatus.FAILED
            job.attempts += 1
            session.commit()