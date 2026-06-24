# EnerSight Auth

Identity provider for EnerSight. Owns the `users` table, issues and validates JWTs, and is the
only service in the platform that ever sees a password. `enersight-api` trusts its tokens but
never talks to it directly — both just share a signing secret.

## What it does

- **Public**: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh`,
  `POST /api/auth/logout`.
- **Self-service** (any authenticated user): `GET/PATCH /api/auth/me`,
  `PATCH /api/auth/me/password`, `DELETE /api/auth/me`.
- **Admin-only** (`ROLE_ADMIN`): `GET/POST/PATCH/DELETE /api/auth/users/**` — list, create,
  approve, change roles, delete.

Login issues a short-lived **access token** (JWT, HS256, 15 min by default) and a long-lived
**refresh token** (opaque, server-tracked, 30 days, single-use/rotating). `/refresh` exchanges a
refresh token for a new pair and revokes the old one; `/logout` revokes a refresh token outright.
New registrations are created unapproved and need an admin's `PATCH /api/auth/users/{id}/approve`
before they can log in.

## Tech stack

Java 17, Spring Boot (Web MVC, Data JPA, Security/OAuth2 Resource Server), PostgreSQL, Flyway,
Lombok.

## Running it

Already has a Docker image and a compose entry — this is the easiest service to start:

```bash
# from backend/docker
docker compose up -d enersight-auth
```

This also starts and waits on its own Postgres (`enersight-auth-db`, internal-network-only — not
published to the host, by design). Listens on **`http://localhost:8082`**.

### Locally instead of Docker

```bash
docker compose up -d enersight-auth-db   # still need its DB
./mvnw spring-boot:run
```

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/enersight_auth` | Postgres connection string |
| `DB_USER` / `DB_PASSWORD` | `auth_user` / `auth_password` | DB credentials |
| `JWT_SECRET` | dev-only literal (see source) | HMAC signing key — **must match `enersight-api`'s** |
| `JWT_EXPIRATION_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_EXPIRATION_DAYS` | `30` | Refresh token lifetime |

A startup check logs a loud `WARN` if `JWT_SECRET` is still the dev-only default — harmless
locally, but a real risk if it ever ships unchanged to a non-local environment.

### Quick manual check

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@tecsys.com","password":"Admin@123"}'
```

## Tests

```bash
./mvnw test
```

Covers JWT issuance/validation, login/register, refresh-token rotation and revocation (including
concurrent-replay handling), self-service (`/me`), and admin user management — both as unit tests
(Mockito) and `@WebMvcTest` controller tests against the real `SecurityConfig`.

## Reset the DB

```bash
docker compose -f ../docker/docker-compose.yaml down -v
docker compose -f ../docker/docker-compose.yaml up -d enersight-auth
```
