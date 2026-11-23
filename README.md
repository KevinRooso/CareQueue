# CareQueue

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
export SPRING_DATASOURCE_PASSWORD=...   # same value as POSTGRES_PASSWORD
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The `demo` profile seeds **25 synthetic referrals** (all statuses, all three reference clinics) on first start — perfect for poking at the database. Re-running is a no-op.

## Try the API

Running locally also needs the two demo-user passwords:

```bash
export CAREQUEUE_NURSE_PASSWORD=local-nurse-pw
export CAREQUEUE_MANAGER_PASSWORD=local-manager-pw
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html — click **Authorize** and log in as `nurse` (clinician) or `manager` (admin), then "Try it out" on any endpoint. The OpenAPI JSON is at `/v3/api-docs`.
- **curl:** `curl -u nurse:local-nurse-pw 'http://localhost:8080/api/waiting-list?clinicId=1'`

All data is synthetic. Users: `nurse` → `CLINICIAN`, `manager` → `ADMIN` (implies `CLINICIAN`).

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
