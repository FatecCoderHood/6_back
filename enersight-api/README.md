# EnerSight API

Spring Boot service serving the analytical/geo data for EnerSight: ANEEL continuity-indicator
(DEC/FEC) geometries over PostGIS, consumed by the frontend's heatmap.

It is a pure **OAuth2 resource server** — it only *validates* JWTs issued by `enersight-auth`
against a shared HMAC secret; it never issues tokens or checks passwords itself. Every endpoint
requires a valid Bearer token.

## Tech stack

- Java 17, Spring Boot (Web MVC, Data JPA, Security/OAuth2 Resource Server)
- PostgreSQL/PostGIS
- Flyway
- Maven (wrapper included, but see note below)

## Running it

This service has no Docker image yet — run it locally against the dockerized database:

```bash
# 1. start the DB (from backend/docker)
docker compose up -d enersight-db

# 2. start the app (from backend/enersight-api)
./mvnw spring-boot:run     # if ./mvnw fails locally, use: mvn spring-boot:run
```

Listens on **`http://localhost:8080`**.

### Required: a matching JWT secret

This service must trust the same signing key as `enersight-auth` to validate its tokens. Both
default to the same dev-only literal, so nothing extra is needed for local dev — but if you ever
override `JWT_SECRET` for `enersight-auth`, set the **same value** here:

```bash
JWT_SECRET=<same value as enersight-auth> ./mvnw spring-boot:run
```

To get a real token for manual testing, log in against `enersight-auth` first (see its README),
then call this service with `Authorization: Bearer <token>`.

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/enersight_app` | Postgres connection string |
| `JWT_SECRET` | dev-only literal (see source) | Must match enersight-auth's secret |

### Database

| Resource | Value |
|---|---|
| DB | `enersight_app` |
| App user | `app_user` |
| Flyway user | `flyway_user` |

### Reset the DB

```bash
docker compose -f ../docker/docker-compose.yaml down -v
docker compose -f ../docker/docker-compose.yaml up -d enersight-db
```

## Tests

```bash
./mvnw test
```

Includes a `SecurityConfigIntegrationTest` that drives the real `SecurityConfig` through MockMvc
(no DB needed — the data service is mocked) to verify token validation end-to-end.

## Build

```bash
./mvnw clean package
java -jar target/*.jar
```

## Common issues

- `init.sql not executed` — check the volume path in `backend/docker/docker-compose.yaml`; a
  stale volume keeps the old init state. Reset with `docker compose down -v`.
- `role "app_user" does not exist` — same cause, same fix: reset the volume.
- `401 Unauthorized` on every request — either no `Authorization` header was sent, or the token
  was signed with a different `JWT_SECRET` than this service is using.
- Port conflict on 5432 — another Postgres is already bound to it; stop it or remap the port in
  `docker-compose.yaml`.
