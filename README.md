# CareQueue

[![CI](https://github.com/KevinRooso/CareQueue/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/KevinRooso/CareQueue/actions/workflows/ci.yml)

CareQueue helps hospital clinics manage GP referrals and waiting lists. It imports referrals from GP systems, uses AI to summarise each one and suggest a priority, and lets a clinician confirm the priority before anyone is booked.

> **Status:** early development. The backend and frontend skeletons are in place; features are being added milestone by milestone (see [Roadmap](#roadmap)).
>
> **All data is synthetic.** No real patient information is used anywhere, including tests and demo data.

## What it will do
- Import referrals as **JSON or XML**. Malformed messages are stored safely as dead letters instead of being lost.
- **AI triage:** summarise a referral and suggest `URGENT`, `SOON` or `ROUTINE`, with red flags. A rule-based fallback is used if the AI is unavailable or returns invalid output.
- **Human in the loop:** the AI never applies a priority. Only a clinician's confirmation does.
- A **waiting list per clinic**, ordered by confirmed priority and then by date received, with appointment booking.
- A **full audit history** of every change, and role-based access (clinician, admin).

## Tech stack
| Area | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4.2, Spring Data JPA / Hibernate 7, Flyway, Spring Security, Actuator |
| Database | PostgreSQL 18 |
| AI | Spring AI 2.1 with Groq (OpenAI-compatible API), behind a provider-agnostic interface |
| Frontend | React 19, TypeScript (strict), Vite 8 |
| Testing | JUnit 5, Testcontainers, JaCoCo, Vitest, React Testing Library |

## Getting started
**Prerequisites:** Java 25, Node 24, Docker Desktop (the integration tests start PostgreSQL with Testcontainers). On Windows, run the scripts in Git Bash.

```bash
cd frontend && npm install && cd ..

./scripts/check.sh --fast   # backend unit tests + frontend typecheck and tests (no Docker)
./scripts/check.sh          # everything: integration tests, coverage report, lint, build
```

Configuration comes from environment variables only; see [`.env.example`](.env.example). Never commit a real `.env`.

### Run locally

```bash
cp .env.example .env          # set POSTGRES_PASSWORD (and GROQ_API_KEY later)
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

A repo-root `.env` (copied from `.env.example`) is read automatically when the app runs
from `backend/` — so once it's filled in, the `cd` line is all you need. Prefer not
to use a file? Export `SPRING_DATASOURCE_PASSWORD` (same value as
`POSTGRES_PASSWORD`) and the two `CAREQUEUE_*_PASSWORD` vars instead; environment
variables beat `.env`.

The `demo` profile seeds **25 synthetic referrals** (all statuses, all three reference clinics) on first start — perfect for poking at the database. Re-running is a no-op.

## Try the API

Running locally also needs the two demo-user passwords:

```bash
export CAREQUEUE_NURSE_PASSWORD=local-nurse-pw
export CAREQUEUE_MANAGER_PASSWORD=local-manager-pw
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html — click **Authorize** and log in as `nurse` (clinician) or `manager` (admin), then "Try it out" on any endpoint. The OpenAPI JSON is at `/v3/api-docs`.
- **The whole flow in Swagger:** authorize as `nurse` → `POST /api/referrals/{id}/triage-suggestion` on a RECEIVED referral → `POST /api/referrals/{id}/triage` with the `version` from the response → re-authorize as `manager` → `POST /api/referrals/{id}/appointment`. Then `GET /api/waiting-list?clinicId=1` no longer shows it — it's booked.
- **curl:** `curl -u nurse:local-nurse-pw 'http://localhost:8080/api/waiting-list?clinicId=1'`
- **Import a referral** (as manager): `curl -u manager:local-manager-pw -X POST http://localhost:8080/api/referrals/import -H 'Content-Type: application/json' --data-binary @samples/referral-sample.json` → `201` + `{id, created:true}`; run it again and you get `200` + `{id, created:false}` — imports are idempotent on `externalRef`. The XML sample works the same way with `-H 'Content-Type: application/xml' --data-binary @samples/referral-sample.xml`.

All data is synthetic. Users: `nurse` → `CLINICIAN`, `manager` → `ADMIN` (implies `CLINICIAN`).

## Run the UI (dev)

Start the backend as described above (it listens on port 8080), then in a second terminal:

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 and log in as `nurse` (clinician) or `manager` (admin) with the
passwords from your `.env` (`CAREQUEUE_NURSE_PASSWORD` / `CAREQUEUE_MANAGER_PASSWORD`).
The Vite dev server proxies `/api` (and `/v3`, `/swagger-ui`) to the backend, so the browser
stays on one origin and no CORS setup is needed.

Credentials are kept in memory only, never in browser storage, so reloading the page logs you
out. This is deliberate: a stored Basic credential would be a long-lived password that any
script on the page could read.

## Run with Docker

The whole app (API, Swagger and the React UI in one image) plus PostgreSQL, with no Java or Node
installed:

```bash
cp .env.example .env    # set POSTGRES_PASSWORD, CAREQUEUE_NURSE_PASSWORD, CAREQUEUE_MANAGER_PASSWORD
                        # (GROQ_API_KEY is optional: blank = rule-based triage)
docker compose up --build
```

- **UI:** http://localhost:8080 – log in as `nurse` (clinician) or `manager` (admin) with the
  passwords from your `.env`.
- **Swagger UI:** http://localhost:8080/swagger-ui.html · **Health:** http://localhost:8080/actuator/health
- The `demo` profile is on by default, so the 25 synthetic referrals are there on first start.
- Port 8080 taken? Set `APP_PORT=18080` in `.env` (Postgres: `POSTGRES_PORT`).
- Stop with `Ctrl+C`. `docker compose down` keeps the data; `docker compose down -v` deletes it.

Compose reads `.env` by itself, only to fill in the `${…}` values in `docker-compose.yml`; the app
container gets just the variables listed there. The image is built by the multi-stage
[`Dockerfile`](Dockerfile): React build (Node 24) → jar with the UI inside (JDK 25) → small JRE 25
image running as a non-root user.

## Deploy to Render (free)

[`render.yaml`](render.yaml) is a Render Blueprint: it describes a free PostgreSQL 18 database and a
free Docker web service, both in Frankfurt. Passwords and the Groq key are not in the file; Render
asks for them once.

1. Push the repository (with `render.yaml` on `master`) to GitHub.
2. Sign in at https://dashboard.render.com (signing in with GitHub is simplest).
3. Click **New** (top right) → **Blueprint**.
4. Connect your GitHub account if asked, give Render access to the repository, and pick
   **`KevinRooso/CareQueue`**.
5. Give the Blueprint a name (e.g. `carequeue`) and keep the branch `master`. Render reads
   `render.yaml` and lists what it will create: the database `carequeue-db` and the web service
   `carequeue`.
6. Enter the three secret values when prompted:
   - `CAREQUEUE_NURSE_PASSWORD` and `CAREQUEUE_MANAGER_PASSWORD` – choose long, unique passwords:
     anyone with the link can reach the login page.
   - `GROQ_API_KEY` – your Groq key for real AI triage, or leave it empty to use the rule-based fallback.
7. Click **Apply**. Render creates the database, then builds the Docker image (the first build takes
   several minutes) and starts the service.
8. Open the **`carequeue`** service in the dashboard and watch **Logs**. It is ready when the deploy
   shows **Live** and the log says `Started CareQueueApplication`.
9. The public URL is at the top of the service page, under its name (like
   `https://carequeue-xxxx.onrender.com`).
10. Open it and log in as `nurse` or `manager` with the passwords from step 6. Swagger is at
    `/swagger-ui.html` on the same URL.

After that, every push to `master` runs GitHub Actions CI, and Render deploys the commit **only if
all checks pass** (`autoDeployTrigger: checksPass`). To change a password or the Groq key later:
service → **Environment** → edit → **Save, rebuild and deploy**.

**Free-tier caveats**
- The service **sleeps after 15 minutes** without traffic. The first request after that wakes it up
  and can take **a minute or more** (Spring starts in about 70 seconds on the free 0.1 CPU).
- The **free database expires 30 days** after it is created. Re-create it from the Blueprint (the
  `demo` profile re-seeds the synthetic data) or upgrade the plan.
- 512 MB of RAM and 0.1 CPU: fine for a demo, not for real load.

## Project layout
```
backend/    Spring Boot application (Maven wrapper: ./mvnw)
frontend/   React + TypeScript + Vite application
evals/      AI triage evaluation dataset
samples/    Synthetic example referrals (JSON, XML, and an invalid XML)
scripts/    check.sh (all checks) and security-scan.sh
```

## Roadmap
1. Data model: schema, entities, audit history, demo data
2. REST API and security
3. JSON/XML referral import with dead letters
4. AI triage suggestions and evals
5. React UI: waiting list, referral detail, import, dead letters
6. Observability and performance
7. Docker image, CI with security scans, deployment
