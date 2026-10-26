# UptimeWatch

[![CI](https://github.com/sinchana14/uptimewatch/actions/workflows/ci.yml/badge.svg)](https://github.com/sinchana14/uptimewatch/actions/workflows/ci.yml)

A self-hosted uptime monitor with a public status page. UptimeWatch checks websites and APIs
every minute, records response times and failures, tracks SSL certificate expiry, and shows
live status with 24-hour and 7-day uptime on a public page.

> Status: **Week 3 complete**: Docker image, a full Docker Compose stack with PostgreSQL,
> Prometheus and Grafana, and a GitHub Actions pipeline that tests, smoke-tests the whole
> stack, scans the image and publishes it to GHCR.
> Coming next: live deployment on Oracle Cloud Always Free (week 4).

## Features

- Checks every monitor on its own interval (default 60 s, minimum 30 s), in parallel
- A site is **up** when it answers 2xx/3xx within 10 s; redirects are followed
- Records status code, response time and error message for every check
- Reads the SSL certificate expiry date from the live TLS session
- **Confirmed-down logic:** a site is only marked DOWN after 2 failed checks in a row, so a single
  network blip never triggers an alert
- **Incidents:** each outage is recorded with start time, end time, duration and cause
- **Alerts:** DOWN, RECOVERED and "SSL expires in N days" messages to Telegram (plus the log)
- Sites grouped by category: India Public Services, Shopping & Food, Developer Tools, Cloud, and more
- 24 h and 7 d uptime percentages computed from raw check history
- Status page: overall banner, summary cards, search, Up/Down filters, category filter,
  per-site details with a response-time chart and incident history, an "Add site" form,
  a dark mode toggle, a mobile layout, and a "How to read this page" guide
- Write operations are protected by an API key; reading status is public
- Old check history is purged daily (30 days by default)

## Architecture

```
            ┌───────────────────────── Spring Boot app ─────────────────────────┐
            │                                                                   │
 every 10s  │  CheckScheduler ──► finds due monitors ──► thread pool (8)        │
 ───────────┼─►                                           │                     │
            │                                             ▼                     │
            │                                     HttpChecker (GET, timeout,    │──► monitored sites
            │                                     SSL expiry, status code)      │
            │                                             │                     │
            │                                             ▼                     │
            │                     MonitorService.recordResult (transaction)     │
            │                       • saves CheckResult                         │
            │                       • updates Monitor snapshot / status flip    │
            │                                             │                     │
            │   MonitorController  ◄── /api/monitors ─────┤                     │
            │   Actuator :8081     ──► /actuator/prometheus ┼──► Prometheus ──► Grafana
            │   static status page ◄── index.html + app.js│                     │
            └─────────────────────────────────────────────┼─────────────────────┘
                                                          ▼
                                       H2 (local dev) / PostgreSQL (production)
```

## Run the full stack with Docker

```bash
docker compose up -d --build
```

| Service | URL | Notes |
|---|---|---|
| Status page | http://localhost:8080 | |
| Prometheus | http://localhost:9090 | Alerts tab shows `SiteDown`, `SiteSlow`, `SslCertificateExpiringSoon`, ... |
| Grafana | http://localhost:3000 | Opens on the **UptimeWatch Overview** dashboard (login `admin` / `admin` to edit) |

Secrets (`UPTIMEWATCH_API_KEY`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`) are read from the
git-ignored `.env.local`. All ports are bound to `127.0.0.1`, so nothing is exposed to your network.

The image is also published by CI: `docker pull ghcr.io/sinchana14/uptimewatch:latest`.

## Observability

The app exposes health checks and Prometheus metrics on a **separate management port (8081)**.
The public port 8080 (and the Cloudflare tunnel) never serves `/actuator/*`.

| Metric | Type | Meaning |
|---|---|---|
| `uptimewatch_monitor_up` | gauge | 1 = UP, 0 = DOWN, per monitor and category |
| `uptimewatch_check_duration_seconds` | histogram | Response time of every check, by monitor and result |
| `uptimewatch_monitor_ssl_days_left` | gauge | Days until each TLS certificate expires |
| `uptimewatch_incidents_open` | gauge | Outages currently in progress |
| `uptimewatch_alerts_sent_total` | counter | Alerts by type, channel and success/failure |

Prometheus alert rules (`monitoring/prometheus/alerts.yml`) include **UptimeWatchDown**, which
watches the watcher: if the app itself stops, no site alerts can go out.

## CI/CD (GitHub Actions)

Every push runs `.github/workflows/ci.yml`:

1. **Build and test**: `mvn verify` (21 tests)
2. **Validate config**: `docker compose config`, `promtool check config` for the Prometheus
   config and alert rules, and a JSON check for the Grafana dashboard
3. **Full-stack smoke test**: builds the image, starts app + PostgreSQL + Prometheus + Grafana,
   then checks that the status page answers, the write API is protected, metrics are not public,
   Prometheus is scraping the app, and the Grafana dashboard loads
4. **Trivy image scan**: reports HIGH/CRITICAL vulnerabilities and **fails the build on any CRITICAL one that has a fix**
5. **Publish**: on `main`, pushes `ghcr.io/sinchana14/uptimewatch:latest` and a `sha-xxxxxxx` tag

## Run locally without Docker

Requirements: Java 17 and Maven.

```bash
mvn spring-boot:run
```

Open http://localhost:8080. 33 starter monitors are added on first run. Sites that block
automated checks (IRCTC times out, NPCI and Swiggy answer 403) are deliberately left out to avoid
false alarms. To see a failure, add `https://expired.badssl.com` from the "Add site" form.

Check history is stored in `./data/` (H2 file database) and survives restarts.

### Run with PostgreSQL

```bash
SPRING_PROFILES_ACTIVE=postgres \
DATABASE_URL=jdbc:postgresql://localhost:5432/uptimewatch \
DATABASE_USERNAME=uptimewatch DATABASE_PASSWORD=secret \
UPTIMEWATCH_API_KEY=choose-a-long-random-key \
mvn spring-boot:run
```

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/monitors` | public | All monitors with status, uptime and last 30 checks |
| GET | `/api/monitors/{id}/checks?limit=60` | public | Recent checks, newest first (max 500) |
| GET | `/api/monitors/{id}/incidents` | public | Incidents for one monitor |
| GET | `/api/incidents?limit=20` | public | Recent incidents across all monitors |
| POST | `/api/admin/verify` | `X-Api-Key` | Check an admin key (used by the Add site form) |
| POST | `/api/admin/test-alert` | `X-Api-Key` | Send a test message to every alert channel |
| POST | `/api/monitors` | `X-Api-Key` | Add a monitor |
| DELETE | `/api/monitors/{id}` | `X-Api-Key` | Remove a monitor and its history |

Add a monitor:

```bash
curl -X POST http://localhost:8080/api/monitors \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: dev-key-change-me" \
  -d '{"name":"My Portfolio","url":"https://example.com","intervalSeconds":60}'
```

The local default key is `dev-key-change-me`. Always set `UPTIMEWATCH_API_KEY` anywhere else.

## Telegram alerts (free)

1. In Telegram, message **@BotFather**, send `/newbot` and follow the steps. It gives you a **bot token**.
2. Send any message to your new bot, then open
   `https://api.telegram.org/bot<TOKEN>/getUpdates` and copy `"chat":{"id": ...}`. That's your **chat id**.
3. Start the app with both values set:

```bash
TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... mvn spring-boot:run
```

4. Check it works: `curl -X POST -H "X-Api-Key: <your key>" http://localhost:8080/api/admin/test-alert`

Never commit the token. Keep it in an environment variable or a git-ignored `.env.local` file.

## Configuration

| Property | Env var | Default |
|---|---|---|
| `uptimewatch.api-key` | `UPTIMEWATCH_API_KEY` | `dev-key-change-me` |
| `uptimewatch.default-timeout-ms` | | `10000` |
| `uptimewatch.checker-threads` | | `8` |
| `uptimewatch.retention-days` | | `30` |
| `uptimewatch.failure-threshold` | | `2` |
| `uptimewatch.ssl-warning-days` | | `14` |
| `uptimewatch.public-url` | `UPTIMEWATCH_PUBLIC_URL` | (empty, used as a link in alerts) |
| `uptimewatch.telegram.bot-token` | `TELEGRAM_BOT_TOKEN` | (empty = disabled) |
| `uptimewatch.telegram.chat-id` | `TELEGRAM_CHAT_ID` | (empty = disabled) |
| `server.port` | `PORT` | `8080` |

## Tests

```bash
mvn test
```

- `HttpCheckerTest` runs real HTTP checks against an in-process server (200, 503, redirect,
  connection refused, User-Agent)
- `MonitorTest` covers confirmed-down logic, recoveries, intervals and SSL warnings
- `MonitorApiTest` covers the REST API, API-key protection, validation, uptime maths,
  incident lifecycle and the alerts each outage produces
- `UptimeWatchMetricsTest` checks the Prometheus output and the health endpoint
- `TelegramNotifierTest` checks the Bot API request against a fake Telegram server and makes
  sure errors never leak the bot token

## Tech stack

Java 17 · Spring Boot 4 · Spring Data JPA · H2 / PostgreSQL · Micrometer · Prometheus · Grafana · Docker · GitHub Actions · Trivy · Vanilla JS · JUnit 5
