# EnerSight backend — instructions

Repository `6_back` (submodule `backend/` of EnerSight). Three services plus the Docker stack. Workspace rules (business rules, branch/commit standard, language, safety) live in the parent repo's `.github/copilot-instructions.md`; the essentials are repeated so this file stands alone.

**Existing code shows the conventions in use, not that it is correct.** Check "Known gaps" before copying a pattern, and flag problems you touch instead of silently replicating or rewriting them.

## Services
| Folder | Stack | Role |
|---|---|---|
| `enersight-api/` | Java 17, Spring Boot 4.0.6 (parent), Maven wrapper, JPA, Flyway, PostgreSQL/PostGIS, Spring Security, Bean Validation, JJWT 0.12.6, Lombok | REST API: auth, users, terms of use, geo data (`/api/geo`) |
| `enersight-worker/` | Python 3.12 (Dockerfile), SQLAlchemy 2, psycopg2, pydantic, requests, GDAL (`ogr2ogr`) | ANEEL ingestion into Postgres `staging.*` |
| `enersight-temporal-series/` | Python, pandas, numpy, Prophet, cmdstanpy, pymongo; FastAPI + motor (`Dockerfile.api`) | Prophet forecasts per unit, MongoDB in/out, read API on port 8000 |
| `docker/` | docker-compose, `init.sql`, `init.js` | PostGIS 16 (5432), MongoDB 7 (27017), mongo-express (8081), worker, job-producer, temporal-series (+API) |

## Commands
- Stack: from `backend/`, `docker compose -f docker/docker-compose.yaml up -d`. Reset DB: `... down -v` then `up -d`.
- API (inside `enersight-api/`): `./mvnw spring-boot:run`, `./mvnw test`, `./mvnw clean package`. `contextLoads` needs a live Postgres.
- Worker: image runs `python -m app.worker`; the producer runs `python -m app.ingestion.job_producer`. Temporal-series: `python -m app` (batch) and `uvicorn main:app` (API). See each README/Dockerfile.
- No Python tests, linter or formatter exist; don't assume `pytest`, `ruff` or `black`.

## Java API conventions (verified in code)
- **Layering:** `controller` → `service` → `repository`; controllers return DTOs, never entities. Mapping is done in service methods (`toResponse`); there are no mapper classes. Keep business rules (e.g. default year) in services, not controllers.
- **Naming:** DTOs `XxxRequestDto` / `XxxResponseDto` (suffix `Dto`); error body `ErrorResponse`; enums PascalCase with UPPER_SNAKE values; custom exceptions are plain `RuntimeException` subclasses in `exception/`.
- **Endpoints:** `/api/<plural-noun>`, no version segment, UUID path variables, English messages. Examples: `/api/auth/login`, `/api/users/register`, `/api/users/{id}/approve-access`, `/api/terms`, `/api/geo`.
- **Lombok:** constructor injection with `@RequiredArgsConstructor` and `private final` fields (no `@Autowired`); entities `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, `@Builder.Default` for defaults; request DTOs `@Data`, response DTOs `@Data @Builder`. Prefer not to put `@Data` on entities with lazy relations (`equals/hashCode/toString` cascade).
- **Validation:** Bean Validation on request DTOs (`@NotBlank`, `@Email`, `@Size(min=8)`, `@NotNull`) triggered by `@Valid @RequestBody`. Add bounds checks for new query params.
- **Errors:** `GlobalExceptionHandler` (`@RestControllerAdvice`) returns `ErrorResponse{status,error,message,timestamp}`. Mapping: not found 404, already exists 409, invalid credentials 401, inactive 403, mandatory terms not accepted 422, validation 400. Any new exception needs a handler entry.
- **JPA:** UUID ids (`GenerationType.UUID`), `@Enumerated(EnumType.STRING)`, `OffsetDateTime` with `@CreationTimestamp`/`@UpdateTimestamp`, `@Table(schema="core")`, LAZY `@ManyToOne`, derived-query repositories. `ddl-auto=validate`.
- **Security:** stateless, BCrypt, JWT with only subject/iat/exp (role is reloaded from DB per request); authorities `ROLE_<role>`; role checks currently happen in services (`hasRole(ADMIN)`), not with `@PreAuthorize`. New protected endpoints must enforce role explicitly and be tested for allowed and denied.
- **Logging:** none exists today. When adding logs use SLF4J (`@Slf4j`), never log passwords, tokens, emails in bulk or full payloads (RNF05).

## Flyway and database (verified)
- Files `src/main/resources/db/migration/V<n>__snake_case.sql`, currently V1–V12; the next is `V13__<snake_case>.sql`. Never edit V1–V12.
- Style: lowercase unquoted identifiers, schema-qualified (`core.`), `gen_random_uuid()`, `TIMESTAMPTZ DEFAULT NOW()`, enums as `VARCHAR` + `CHECK` (when adding an enum value, update the check, as V10 did). Constraint prefixes `uq_`, `chk_`, `fk_`. Exception: V4 used a native enum type.
- Schemas `core` (users, terms, acceptances) and `staging` (ingestion jobs, ANEEL data). Migrations run as `flyway_user`, the app as `app_user`.
- Seeds must not embed real credentials. The existing admin seed (V9) is a known weak credential: flag it; don't copy the pattern.
- Docker `init.sql` creates the roles/database; the worker writes `staging.*` tables, so schema ownership is split between Flyway (API) and the worker — confirm before changing either.

## Python conventions (verified)
- **Style:** snake_case; PascalCase classes; settings as UPPER_CASE attributes of a `Settings` class reading `os.getenv` (no pydantic-settings); SQLAlchemy 2 `Mapped`/`mapped_column`; `IngestionStatus` enum: pending, processing, done, failed.
- **Logging:** worker uses a module logger with `"module::function - message"` in English; temporal-series logs in Portuguese with `%s` args. Follow the service you edit.
- **Language:** worker code/comments English; temporal-series docstrings/docs Portuguese (keep).
- **Queue:** `staging.ingestion_jobs` is the queue (`FOR UPDATE SKIP LOCKED`, `ON CONFLICT (url) DO NOTHING`). There is no retry for FAILED jobs and no recovery of stuck PROCESSING jobs.
- **Temporal-series:** Prophet yearly seasonality only, `n_changepoints=10`, horizon 12 months (`MS`), minimum 24 samples; MAE computed on the last 12 months when enough data. Destination collections are recreated each run; `append_forecasts` checkpoints every 500 units.
- **Env vars:** names only in docs (`DB_*`, `MONGO_*`, `SOURCE_DB`, `OUTPUT_DB`, `ANO_MINIMO`, `HORIZONTE`, …); never write values.
- Pin versions in `requirements.txt` as existing; `requirements_api.txt` is unpinned (`>=`) — don't extend that habit. Don't bump `cmdstanpy==1.2.4` (explained in its comment).

## Known gaps (mention, don't silently change)
**Living notes:** verified state and backlog per area are in the workspace repo, `docs/project-memory/` (start with `authentication.md` for login, registration, JWT and roles). Read the note before touching the area, and update it in the same change when you fix or touch an item. The `[back]` items there are this module's backlog.

**API**
- `jwt.secret` and DB credentials are literals in tracked `application.properties`; only `DB_URL` is an env var.
- `SecurityConfig` permits all `GET /**` and `POST /**`; `approve-access` and term updates have no role check; `GET /api/users/{id}` has no ownership check.
- `TermsOfUseService.create` compares the enum `TermType` to the string `"MANDATORY"` with `equals` (always false) and throws an unhandled `IllegalArgumentException`.
- `UserPendingException`, `AccessDeniedException` and generic exceptions have no handler (likely 500).
- Terms `update` overwrites content and bumps `version` in place; acceptances don't store the term version (RF03 history). No revoke endpoint, no anonymization (`delete` is a hard delete), no audit/log trail (RF04, RF05).
- V12 dropped `fk_uta_user`, so acceptances can be orphaned. V9 documents a known admin password in a comment.
- `/api/geo` has no pagination or bounds validation, maps native-query rows by array index, and is hard-coded to `enel_sp_*` tables.
- Docs mismatch: README says Java 21+/Spring Boot 3+/Swagger; `pom.xml` is Java 17/Spring Boot 4.0.6 with no OpenAPI dependency.
- Only `contextLoads` exists as a test.
- Maven wrapper is broken: no `.mvn/` directory (only a stray `enersight-api/wrapper/`; `.mvn/wrapper/maven-wrapper.jar` is tracked at the wrong path) and `mvnw` lacks the execute bit. Use system `mvn spring-boot:run` until fixed.
- `application.properties` sets `hibernate.dialect` explicitly (deprecated warning) and leaves `spring.jpa.open-in-view` at its default (on).
- Run verified (2026-10-30): DB healthy, 12 Flyway migrations OK, API up on 8080 with 0 errors; frontend proxy works. WSL has no `node` on PATH (use nvm's `v24.14.1` by full path).

**Python / Docker**
- The worker pipeline never loads (load call commented out); the transformer is a stub; loader uses `ogr2ogr -overwrite` (not idempotent) with `host=localhost` and a personal path in its `__main__`; no validation or batch id (RF07). No scheduler or admin trigger for collection (RF06). `jobs/run_ingestion.py` is stale.
- Temporal-series FastAPI uses deprecated `@app.on_event`; API has no authentication; `append_forecasts` may duplicate on rerun (not verified).
- Compose and `settings.py` contain literal passwords; DB, Mongo and mongo-express ports are published to the host.
- `read_ssdmt*.py` and the Prophet notebook are stray scripts; `/tmp/data` is never cleaned; the zip extractor has no size checks.

## Git and delivery
Branch `<TASK-ID>/<lowercase-hyphenated-description>` from `develop`; commit `<TASK-ID> <type>: <short lowercase description>` (feat, fix, chore, docs, style, refactor, perf, test, build, ci, revert, hotfix). PR to `develop`, two approvals. Commit/push only when asked. Don't commit `.venv`, `target/`, `__pycache__`, large CSVs or secrets.
