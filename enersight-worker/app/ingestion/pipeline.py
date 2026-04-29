from pathlib import Path
from app.ingestion.downloader import download
from app.ingestion.extractor import extract
from app.ingestion.transformer import transform
from app.ingestion.loader import load_to_postgis
from app.config.settings import settings
from app.utils.logger import logger

class IngestionPipeline:

    def run(self, job):
        base = Path(f"{settings.DATA_DIR}/{job.job_id}_{job.title}")
        logger.info(f"IngestionPipeline::run - Pipeline base path: {base}")

        file_path = download(job.url, base)
        extracted = extract(file_path, base)
        transformed = transform(extracted)

        logger.info(f"IngestionPipeline::run - Intentionally skipping load postgis on db for testing purposes")
        # load_to_postgis(str(transformed), "raw_data")