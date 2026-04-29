import time
from app.db.repositories import (
    fetch_and_lock_job,
    mark_job_done,
    mark_job_failed
)
from app.ingestion.pipeline import IngestionPipeline
from app.utils.logger import logger

def worker_loop():
    logger.info("worker::run_worker - Starting worker...")

    pipeline = IngestionPipeline()

    while True:
        job = fetch_and_lock_job()

        if not job:
            logger.info("worker::run_worker - No jobs found, sleeping...")
            time.sleep(5)
            continue

        try:
            logger.info(f"worker::run_worker - Processing job {job.job_id}: {job.title} ({job.url})")
            pipeline.run(job)
            mark_job_done(job.job_id)

        except Exception as e:
            logger.error(f"worker::run_worker - Job {job.job_id} failed: {e}")
            mark_job_failed(job.job_id)

if __name__ == "__main__":
    worker_loop()