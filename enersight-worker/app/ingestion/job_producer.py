import time
import uuid
import requests
from sqlalchemy import text
from app.utils.logger import logger
from app.db.connection import get_engine
from app.utils.parser import try_parse_uuid
from app.models.schemas import IngestionJob

INITIAL_URL = "https://hub.arcgis.com/api/search/v1/collections/all/items?filter=((group IN (4cb2fc35aabf4f1bb3d552f616c05f94, 43608dfe83594d9fb3f7df28d2b49dea))) AND ((type IN ('Document Link'))) AND ((tags IN (distribuicao)))&limit=99"

"""
{
    "features": [
        {
            "properties": {
                "id": "415cd23211284f0f8358270983585648",
                "orgId": "J5unWNi0P2dwjI3y",1
                "title": "EQT_AL_44_2023-11-30_V11_20240426-1150 - Link",
                "description": "Link direto para download do File Geodatabase.",
                "url": "https://aneel.maps.arcgis.com/sharing/rest/content/items/093c6e8230c04e39a279611997743861/data"
            }
        }
    ]
}
"""

"""
raw_uuid = "415cd23211284f0f8358270983585648"

# Converte a string hex em um objeto UUID e depois em string formatada
formatted_uuid = str(uuid.UUID(raw_uuid))

print(formatted_uuid) 
# Resultado: 415cd232-1128-4f0f-8358-270983585648
"""


def fetch_pages_with_retries(url: str, retries: int = 3) -> dict:
    for attempt in range(retries):
        logger.info(f"job_producer::fetch_pages_with_retries - Trying [{attempt+1}/{retries}] fetch: {url}")

        try:
            return fetch_pages(url)

        except requests.exceptions.HTTPError as e:
            status = e.response.status_code

            if 400 <= status < 500:
                logger.error(f"job_producer::fetch_pages_with_retries - Client error {status}, not retrying: {url}")
                raise  # don't retry client errors

        except Exception as e:
            logger.warning(f"job_producer::fetch_pages_with_retries - Attempt {attempt+1} failed for {url}: {e}")

        if attempt < retries - 1:
            time.sleep(2 ** attempt)  # Exponential backoff

    raise Exception(f"All retries failed for {url}")

def fetch_pages(url: str) -> dict | None:
    try:
        response = requests.get(url, timeout=30, headers={"User-Agent": "enersight-ingestor"})
        response.raise_for_status()
        return response.json()
    
    except requests.exceptions.Timeout:
        logger.error(f"Timeout while fetching {url}")
        raise

    except requests.exceptions.ConnectionError:
        logger.error(f"Connection error while fetching {url}")
        raise

    except requests.exceptions.HTTPError as e:
        logger.error(f"HTTP error {e.response.status_code} for {url}")
        raise

def extract_sources(items: list) -> list:
    logger.info(f"job_producer::extract_sources - Extracting sources from {len(items)} items")

    sources = []

    for item in items:
        id = item.get("properties", {}).get("id")
        orgId = item.get("properties", {}).get("orgId")
        title = item.get("properties", {}).get("title")
        description = item.get("properties", {}).get("description")
        url = item.get("properties", {}).get("url")

        job = IngestionJob(
            job_id=try_parse_uuid(id),
            org_id=orgId,
            title=title,
            description=description,
            url=url
        )

        sources.append(job)

    return sources

def insert_jobs(engine, jobs: list):
    logger.info(f"job_producer::insert_jobs - Inserting {len(jobs)} jobs into database")

    rows = [
        {
            "job_id": str(job.job_id),
            "org_id": job.org_id,
            "title": job.title,
            "description": job.description,
            "url": str(job.url)
        }
        for job in jobs
    ]

    if not rows:
        return
    
    with engine.begin() as conn:
        conn.execute(
            text("""
                INSERT INTO staging.ingestion_jobs
                (job_id, org_id, title, description, url)
                VALUES
                (:job_id, :org_id, :title, :description, :url)
                ON CONFLICT (url) DO NOTHING
            """),
            rows
        )

def get_next_link(links: list) -> str | None:
    logger.info(f"job_producer::get_next_link - Getting next link for URL...")

    if len(links) == 0:
        return None
    
    for link in links:
        if link.get("rel") == "next":
            return link.get("href")

    return None

def run_job_producer():
    logger.info("job_producer::run_job_producer - Starting EnerSight job producer...")

    engine = get_engine()
    url = INITIAL_URL

    while url:
        try:
            payload = fetch_pages_with_retries(url)
        except Exception as e:
            logger.error(f"job_producer::run_job_producer - Failed to fetch page: {e}")
            break

        items = payload.get("features", [])
        links = payload.get("links", [])

        sources = extract_sources(items)
        insert_jobs(engine, sources)

        url = get_next_link(links)

        if not url:
            logger.info("job_producer::run_job_producer - No more pages to fetch, exiting loop")
            break
        else:
            logger.info(f"job_producer::run_job_producer - Next page URL: {url}")
    
    logger.info("job_producer::run_job_producer - Finishing EnerSight job producer...")

if __name__ == "__main__":
    run_job_producer()
