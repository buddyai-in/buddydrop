# BuddyDrop

Secure file transfer &amp; storage on Amazon S3, with passwordless **magic-link** sign-in.

Sign in with a one-time email link, keep a personal library of files, and hand any file to
someone else with an expiring, optionally password-protected share link. File bytes travel
**directly between the browser and S3** over presigned URLs — they never pass through the app.

- **Stack:** Spring Boot 3.5 · Java 17 · Thymeleaf · Spring Security · Spring Data JPA + Flyway
- **Storage:** Amazon S3 (AWS SDK v2), presigned `PUT`/`GET`
- **Database:** PostgreSQL in production, in-memory H2 for local dev &amp; tests
- **UI:** server-rendered Thymeleaf styled with CSS custom-property tokens (no jQuery/React/Tailwind)

---

## Quick start (local dev)

No database or mail server required — dev mode uses in-memory H2 and logs the magic link to the
console instead of emailing it.

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080>:

1. Enter any email address and submit.
2. Copy the `auth/verify?token=…` URL printed in the console log and open it.
3. Click **Sign in** on the confirm page — you're in.

> Uploads/downloads need real S3 credentials (see below). Everything else — auth, the dashboard,
> sharing UI — works fully against H2 with no AWS setup.

### Running the tests

```bash
mvn test
```

Tests run entirely in-memory (H2 + a fake storage backend); no AWS or network access needed.

---

## Run with Docker

A multi-stage `Dockerfile` (Maven build → slim non-root JRE) and a `docker-compose.yml` (app +
PostgreSQL) are included.

```bash
cp .env.example .env      # fill in AWS creds + bucket (email defaults to console logging)
docker compose up --build
```

The stack runs the `prod` profile against PostgreSQL. `BUDDYDROP_COOKIE_SECURE=false` (set in the
compose env) lets the session cookie work over plain HTTP on localhost — set it back to `true`
behind HTTPS. App comes up on <http://localhost:8080> with a `/actuator/health` healthcheck.

### Publish to Docker Hub

```bash
# builds, tags :<version> and :latest, and pushes
DOCKER_USERNAME=you ./scripts/docker-publish.sh
```

Env knobs: `DOCKER_IMAGE` (default `$DOCKER_USERNAME/buddydrop`), `TAG` (defaults to the pom
version), `DOCKER_PASSWORD` (a Docker Hub access token → auto `docker login`), `PUSH_LATEST`, and
`PLATFORMS` (e.g. `linux/amd64,linux/arm64` for a multi-arch buildx push). It also reads `.env`.

### Deploy to EC2

`scripts/deploy-ec2.sh` builds the image, pushes it to Docker Hub (reusing `docker-publish.sh`),
then over SSH pulls it on an EC2 host and brings up a production stack: **[Caddy](https://caddyserver.com/)**
auto-provisions Let's Encrypt TLS and reverse-proxies to the app. The database is **external**
(e.g. RDS) — point `BUDDYDROP_DB_URL` at it. The deploy files live in `deploy/`.

**One-time setup on the host:** install Docker + the compose plugin, and open the security group
for inbound `22`, `80`, and `443`. Point your domain's DNS at the host. (For a private image, run
`docker login` on the host once — the script does not forward registry credentials.)

**From your machine:**

```bash
cp deploy/deploy.env.example  deploy/deploy.env    # EC2 host + Docker Hub settings
cp deploy/.env.prod.example   deploy/.env.prod     # app runtime env (domain, RDS, S3, mail)
# fill both in, then:
./scripts/deploy-ec2.sh                            # or: ./scripts/deploy-ec2.sh 1.2.3  (explicit tag)
```

The script ships `deploy/docker-compose.prod.yml`, the `Caddyfile`, and `deploy/.env.prod` (as
`.env`) to `EC2_DEPLOY_DIR`, runs `docker compose pull && up -d`, and waits for `/actuator/health`
to report `UP`. Both `deploy/deploy.env` and `deploy/.env.prod` are gitignored — commit only the
`*.example` templates. Re-running redeploys; set `SKIP_PUSH=true` for a config-only redeploy of the
current tag.

---

## How it works

### Passwordless sign-in

```
POST /auth/request  → mint token, store only its SHA-256 hash (TTL 15m), email a link
GET  /auth/verify   → show a confirm page (does NOT consume the token)
POST /auth/verify   → consume the token once, open an HttpOnly session → /files
```

The **GET/POST split** is deliberate: email security scanners pre-fetch links, and a `GET` here
only previews the token, so a scanner can't silently burn a single-use link — only the human's
`POST` consumes it. Requests are rate-limited per email.

### Direct-to-S3 uploads

```
POST /api/files/presign-upload  → quota check → presigned PUT URL + new file id (row = PENDING)
   (browser PUTs the bytes straight to S3)
POST /api/files/{id}/confirm    → read real size from S3, mark READY (re-checks quota)
GET  /api/files/{id}/download   → 302 to a presigned GET URL (Content-Disposition = original name)
```

### Transfer / sharing

```
POST   /api/files/{id}/share  → create/update a share; returns the URL when a token is minted
GET    /s/{token}             → public download page (password prompt if set)
POST   /s/{token}             → verify + count the download → 302 to a presigned GET
DELETE /api/files/{id}/share  → revoke
```

Share tokens are stored as SHA-256 hashes and shown once at creation; passwords are BCrypt-hashed;
expiry and download caps are enforced server-side on every public hit.

See [`AGENTS.md`](AGENTS.md) for the full architecture map and a "where things live" index.

---

## Configuration

All settings bind from the `buddydrop.*` tree (see `AppProperties`) and can be overridden by
environment variables. Key ones:

| Variable | Meaning | Default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` (H2) or `prod` (PostgreSQL + SMTP) | `dev` |
| `BUDDYDROP_BASE_URL` | Absolute base URL used in emailed links | `http://localhost:8080` |
| `BUDDYDROP_S3_BUCKET` | Target S3 bucket | `buddydrop-dev` |
| `AWS_REGION` | Bucket region | `us-east-1` |
| `BUDDYDROP_S3_ENDPOINT` | S3-compatible endpoint override (MinIO/LocalStack) | *(none)* |
| `BUDDYDROP_STORAGE_CONFIGURE_CORS` | Apply bucket CORS on startup (needs `s3:PutBucketCORS`) | `false` (dev: `true`) |
| `BUDDYDROP_QUOTA_BYTES` | Per-account storage quota | 10 GB |
| `BUDDYDROP_MAX_UPLOAD_BYTES` | Max single upload | 5 GB |
| `BUDDYDROP_UPLOAD_LIMIT_HOURLY` / `_DAILY` / `_MONTHLY` | Per-user upload caps (rolling windows; `0` disables) | 50 / 500 / 5000 |
| `BUDDYDROP_DOWNLOAD_LIMIT_HOURLY` / `_DAILY` / `_MONTHLY` | Per-user download caps (rolling windows; `0` disables) | 200 / 2000 / 20000 |
| `BUDDYDROP_MAIL_PROVIDER` | Email transport: `log`, `ses`, or `smtp` | `log` (dev) / `ses` (prod) |
| `BUDDYDROP_MAIL_FROM` | From address on emails | `no-reply@buddydrop.app` |
| `BUDDYDROP_SES_REGION` | SES region (falls back to `AWS_REGION`) | `us-east-1` |
| `BUDDYDROP_SES_CONFIG_SET` | Optional SES configuration set | *(none)* |

**Production** (`prod` profile) additionally reads `BUDDYDROP_DB_URL` / `BUDDYDROP_DB_USER` /
`BUDDYDROP_DB_PASSWORD`. AWS credentials come from the standard provider chain (env vars, profile,
or IAM role) — never hardcoded.

### Rate limits

Uploads and downloads are capped per user across rolling **hour / day / month** windows, counted in
the `usage_event` table (persistent, so the monthly window survives restarts and is shared across
nodes). Downloads count both the owner's own downloads **and** anonymous downloads through that
owner's share links. Exceeding a window returns HTTP `429` (a friendly page on the public share
route). Set any limit to `0` to disable that window. Defaults are in the table above.

### Email transport

Email delivery is pluggable via `BUDDYDROP_MAIL_PROVIDER`, selecting one `EmailSender`:

- **`log`** (default in dev) — writes the message to the console instead of sending; the magic-link
  URL appears in the log so you can sign in with no mail infrastructure.
- **`ses`** (default in prod) — Amazon SES via the AWS SDK. No SMTP credentials; uses the same AWS
  credential chain as S3. The from address must be a **verified SES identity** (or verified domain),
  and while your account is in the SES sandbox, recipients must be verified too. Grant the app's IAM
  principal `ses:SendEmail`.
- **`smtp`** — any SMTP relay via `spring.mail.*` (`BUDDYDROP_SMTP_HOST` / `_PORT` / `_USER` /
  `_PASSWORD`), including the SES SMTP interface if you prefer it over the API.

### S3 bucket expectations

- Block all public access — reach is only ever granted through a presigned URL.
- Enforce server-side encryption (SSE-S3 or SSE-KMS).
- Allow the app's IAM principal `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`, `s3:HeadObject`.
- **CORS** — browser uploads `PUT` directly to S3, which triggers a CORS preflight, so the bucket
  must allow the app's origin. Two ways:
  - *Automatic (dev):* set `BUDDYDROP_STORAGE_CONFIGURE_CORS=true` (already on in the `dev` profile).
    On startup the app applies the rule below for `base-url`; the credentials then also need
    `s3:PutBucketCORS`. If that permission is missing it logs the exact policy instead of failing.
  - *Manual (prod):* leave auto off and set the bucket CORS in the S3 console
    (Permissions → CORS), replacing the origin with your deployed URL:

    ```json
    [
      {
        "AllowedOrigins": ["https://buddydrop.app"],
        "AllowedMethods": ["PUT", "GET", "HEAD"],
        "AllowedHeaders": ["*"],
        "ExposeHeaders": ["ETag"],
        "MaxAgeSeconds": 3000
      }
    ]
    ```

---

## Project layout

```
src/main/java/com/buddyai/buddydrop/
├─ auth/        magic-link sign-in, rate limiting, session
├─ config/      typed properties + S3 client beans
├─ domain/      JPA entities (AppUser, MagicToken, StoredFile, ShareLink)
├─ repository/  Spring Data repositories
├─ storage/     StorageService abstraction + S3 implementation
├─ file/        file lifecycle service + JSON API
├─ share/       sharing service, owner API, public download controller
├─ usage/       per-user upload/download rate limiting (hour/day/month)
├─ mail/        transactional email (EmailSender: SES / SMTP / dev-logging)
├─ security/    Spring Security config + principal
├─ web/         dashboard view controller, error handling, display helpers
└─ exception/   domain exceptions mapped to HTTP status
src/main/resources/
├─ db/migration/  Flyway (V1__baseline.sql)
├─ templates/     Thymeleaf views
└─ static/        tokens.css, app.css, app.js
```
