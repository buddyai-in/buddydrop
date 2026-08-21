# AGENTS.md — BuddyDrop architecture map

Authoritative orientation for anyone (human or AI) working in this repo. Read this before grepping.

## What this is

A single self-contained Spring Boot service: secure file transfer &amp; storage on S3 with
passwordless magic-link auth. Base package `com.buddyai.buddydrop`. **Not** multi-module — the
surface is small enough that one module is the right size; don't add module boundaries speculatively.

## The two capabilities

1. **Personal library** — every signed-in user uploads/lists/downloads/deletes their own files
   (scoped by owner id from the session).
2. **Transfer** — any owned file can become a public share link (expiry, download cap, optional
   password) that recipients open with no account.

Both sit on the same S3 objects; the difference is who may reach them and for how long.

## Request flows (the load-bearing designs)

- **Magic link.** `AuthController` + `MagicLinkService`. Tokens are stored only as SHA-256 hashes,
  TTL 15m, single-use. The `GET /auth/verify` *previews* the token; only `POST /auth/verify`
  consumes it — so an email scanner's pre-fetch can't burn a link. Session is HttpOnly, set
  programmatically (there is no password login).
- **Direct-to-S3 transfer.** Bytes never flow through the JVM. `FileService.initiateUpload` does a
  quota check then issues a presigned `PUT`; the browser uploads to S3; `confirmUpload` reads the
  **real** size from S3 (client size is not trusted) and flips the row `PENDING → READY`.
- **Public share.** `PublicShareController` + `ShareService`. Expiry / cap / password are enforced
  server-side on every hit; the download 302-redirects to a presigned `GET`.

## Layer boundaries (respect these)

```
web/  auth/  share/(controllers)   ← HTTP, Thymeleaf, JSON
        │
   *Service  (auth/ file/ share/ mail/)   ← business logic, @Transactional
        │
StorageService (abstraction) ──► S3StorageService   ← all S3 access lives here
        │
 repository/  ← Spring Data JPA
        │
 domain/      ← entities
```

- **All S3 access goes through `StorageService`.** Never call the SDK from a controller or another
  service. Tests swap in an in-memory fake via this seam.
- **File ↔ share decoupling.** `FileService` depends on the narrow `ShareCleanup` interface (so a
  delete tears down shares); `ShareService` *implements* it and checks ownership via the file
  repository directly. This keeps the dependency one-directional — do not make `ShareService`
  depend on `FileService` (it creates a constructor cycle).
- **Ownership is always server-side.** Every file/share operation is scoped by the session user's
  id; the id in a URL is never trusted alone.

## Conventions

- **Lombok** for boilerplate (`@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) — don't
  hand-write getters/constructors/loggers.
- **Config** is typed in `AppProperties` (`buddydrop.*`); don't scatter `@Value`. Secrets come from
  env / the AWS provider chain — never hardcoded.
- **Tokens** (magic + share) go through `util/Tokens` — generate raw, store only the hash.
- **Schema** is owned by Flyway (`db/migration`), and `spring.jpa.hibernate.ddl-auto=validate`.
  Change the schema by adding a `V2__*.sql`, never by editing V1 or relying on Hibernate DDL. Types
  are chosen to validate on both PostgreSQL and H2 (`uuid`, `timestamp with time zone`).
- **UI:** extend the Thymeleaf fragments in `templates/fragments/` and use `var(--…)` tokens from
  `static/css/tokens.css`. No jQuery/React/Tailwind; no hardcoded colors. Dashboard behavior is in
  `static/js/app.js` (vanilla). View controllers pre-format display strings (dates, sizes, share
  labels) via `web/DisplayLabels` so templates only print strings.
- **Errors:** throw the domain exceptions in `exception/`; `web/GlobalExceptionHandler` maps them to
  JSON for `/api/**`. Public share pages render friendly HTML for the "link is gone" cases.

## Where things live

| Looking for… | It's in… |
| --- | --- |
| Sign-in / token logic | `auth/MagicLinkService`, `auth/AuthController` |
| Rate limiting | `auth/RateLimitService` |
| Quota, upload presign/confirm, delete | `file/FileService` |
| Dashboard JSON API | `file/FileController` |
| S3 presigning / delete / head | `storage/S3StorageService` |
| Share create/revoke/resolve, cap/expiry/password | `share/ShareService` |
| Public download page | `share/PublicShareController`, `templates/share/*` |
| Dashboard page assembly | `web/ViewController`, `templates/dashboard.html` |
| Security rules / session | `security/SecurityConfig` |
| Config knobs | `config/AppProperties`, `resources/application*.yml` |
| Schema | `resources/db/migration/V1__baseline.sql` |

## Build &amp; test

- Iterate: `mvn -DskipTests package` · Run app: `mvn spring-boot:run` (dev profile, H2).
- Targeted test: `mvn test -Dtest=ShareServiceTest` · Full gate: `mvn test`.
- Tests are fully in-memory (H2 + `support/FakeStorageService`); no AWS/network needed.
