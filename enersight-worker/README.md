# EnerSight Worker

Python service that ingests ANEEL's public continuity-indicator (DEC/FEC) datasets into
PostgreSQL/PostGIS, so `enersight-api` has geo data to serve.

## What it does

Two cooperating pieces, both reading/writing a `staging.ingestion_jobs` table:

- **`enersight-job-producer`** (`app/ingestion/job_producer.py`) — queries ANEEL's ArcGIS Hub API
  for available distribution datasets and enqueues one row per dataset into
  `staging.ingestion_jobs`. Runs once and exits.
- **`enersight-worker`** (`app/worker.py`) — polls that table for `pending` jobs, and for each one
  runs the pipeline (`app/ingestion/pipeline.py`): download → extract → transform → load. Runs
  continuously, sleeping between polls when the queue is empty.

The geometry load step (`app/ingestion/loader.py`) shells out to `ogr2ogr` to load a GDB's
`SSDMT` layer into `staging.enel_sp_ssdmt`. A separate, manually-run script
(`app/ingestion/import_man_indq.py`) loads the indicator/limit spreadsheet into
`staging.enel_sp_indq` via pandas. **Both of `enel_sp_ssdmt`/`enel_sp_indq` are required for
`enersight-api`'s `/api/geo` endpoint to return anything** — if that endpoint 500s with
`relation "staging.enel_sp_indq" does not exist`, this ingestion hasn't been run yet.

## Tech stack

Python, SQLAlchemy, psycopg2, pydantic, `ogr2ogr` (GDAL) for geometry loading.

## Running it

```bash
# from backend/docker — runs the job producer once, then the worker continuously
docker compose up -d enersight-db
docker compose up enersight-job-producer
docker compose up -d enersight-worker
```

### Locally instead of Docker

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m app.ingestion.job_producer   # enqueue jobs, runs once
python -m app.worker                   # process the queue, runs continuously
```

### Manual indicator import

`import_man_indq.py` and `loader.py`'s `__main__` block currently point at hardcoded local file
paths (search for `DOWNLOADS_PATH`/the `.gdb` path) — edit those to wherever you've downloaded the
ANEEL GDB/spreadsheet before running:

```bash
python -m app.ingestion.import_man_indq
python -m app.ingestion.loader
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `enersight-db` | Postgres host (use `localhost` when running outside Docker) |
| `DB_NAME` | `enersight_app` | Database name |
| `DB_USER` / `DB_PASSWORD` | `app_user` / `app_password` | DB credentials |

## Tests

No automated test suite exists yet for this service. Verify ingestion manually by checking row
counts in `staging.enel_sp_ssdmt` / `staging.enel_sp_indq` after a run, or by confirming
`enersight-api`'s `/api/geo` returns data for a known bounding box.
