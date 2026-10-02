# osu! tournament backend

Ktor backend for a small osu! tournament project.

## Features

- PostgreSQL with two main tables: `players` and `scores`.
- Scheduled sync every minute:
  - reads players from `players`
  - fetches latest recent score from osu! API v2
  - saves score into `scores`
- Raw osu score JSON is stored in `scores.raw_json`.
- Docker Compose setup for backend + Postgres.

## Setup

1. Fill root `.env` (one level above `backend`): `POSTGRES_*` or `DATABASE_*`, `DATABASE_JDBC_URL`, `OSU_CLIENT_*`.
   Docker Compose loads it automatically. In IntelliJ use run configuration **Backend** (reads `../.env`) or set the same variables manually.
2. Start services from the project root:

```bash
docker compose up --build
```

Docker build uses BuildKit layer cache (Gradle deps + `.docker-cache/backend` between runs).
First build is still slow; after that only changed `src/` is recompiled.
Ensure BuildKit is on (`DOCKER_BUILDKIT=1`, default in Docker Desktop).

## API

- `GET /health`
- `GET /players`
- `POST /players`
  - body: `{ "id": 1234567, "username": "some_player_name" }` (`id` — osu! user id)
- `GET /players/{id}/scores`

## Notes

- `scores.id` — SHA-256 hex hash of `osu_score_id|user_id|created_at` (osu id may be `0` for failed scores).
- Duplicate pulls are ignored via `INSERT ... ON CONFLICT DO NOTHING` on `scores.id`.
- After changing the scores schema, reset the table: `DROP TABLE scores;` and restart the backend (Exposed recreates it).
- Scheduler interval is 60 seconds.
