# Backend Dev Notes (for Coding Agents / multi-model collaboration)

This document helps you (and different coding agents/models) quickly restore context and produce safe, consistent changes. **Keep all local secrets in `.env` and never commit them to git** (already ignored by `.gitignore`).

## Overview

- Stack: Java 17 + Spring Boot (Gradle Wrapper) + Spring Security + JPA + PostgreSQL
- OpenAPI: `springdoc-openapi` (usually available at `GET /swagger-ui.html` or `GET /swagger-ui/index.html`)
- Default port: controlled by `PORT` (locally typically `8080`)
- Config: `src/main/resources/application.yml`, and local `.env` is loaded via `spring.config.import=optional:file:.env[.properties]`

## Requirements

- JDK 17+
- (Optional) Docker for containerized / consistent runtime

## Quick start (recommended)

From `gagent-backend/`:

Windows (PowerShell):

```powershell
.\gradlew.bat bootRun
```

macOS/Linux/WSL:

```bash
./gradlew bootRun
```

Common commands:

```bash
./gradlew test
./gradlew bootJar
./gradlew clean
```

## Environment variables (never put values in docs/commits)

The backend reads these variables from `.env` (as referenced by `application.yml`):

- `OPENAI_API_KEY`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `PORT`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `FRONTEND_URL` (default `http://localhost:5173`)

Notes:

- `DB_*` are for PostgreSQL (JPA `ddl-auto: update`; be mindful of local data consistency and migration strategy).
- OAuth/Google variables are used for sign-in and Gmail scopes.
- Changing `JWT_SECRET` invalidates previously issued tokens.

## Docker

This project includes a `Dockerfile` (multi-stage build, runs the jar, exposes `8080` by default).

Example (from `gagent-backend/`):

```bash
docker build -t gagent-backend .
docker run --rm -p 8080:8080 --env-file .env gagent-backend
```

## Integration expectations

- Frontend default: `http://localhost:5173`
- Backend default: `http://localhost:8080`
- If you touch CORS / OAuth redirects: start by locating the Spring Security config and allowed origins, then change them intentionally.

## Working agreements for Coding Agents

- **Never commit sensitive data**: do not commit `.env`; do not bake secrets into code/README/test snapshots.
- **Be cautious with security changes**: Spring Security changes can impact login, callbacks, CORS, CSRF, and Session/JWT behavior.
- **DB-related changes**: prefer backward-compatible changes; add a migration note when needed (even if it’s manual SQL for now).
- **API changes must be synchronized**: update DTOs, validation, and error response shape; keep a safe upgrade path for the frontend.
- **Keep it runnable**: avoid breaking local `bootRun` and `test`; use minimal changes to restore health when needed.

