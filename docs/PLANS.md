# Subscription plans — design & roadmap

How BuddyAi Drop-In moves from one-size-fits-all limits to **Free + paid tiers**, without a
disruptive rewrite. The guiding principle: **the Free plan's allowances equal today's defaults**, so
shipping the plan model changes *no behavior* until a user is actually upgraded.

## 1. Tiers

| Dimension | **Free** (today's defaults) | **Pro** | **Business** |
| --- | --- | --- | --- |
| Storage quota | 10 GB | 200 GB | 2 TB |
| Max single file | 5 GB | 10 GB | 50 GB |
| Uploads (hour / day / month) | 50 / 500 / 5,000 | 200 / 2,000 / 20,000 | 1,000 / 10,000 / 100,000 |
| Downloads (hour / day / month) | 200 / 2,000 / 20,000 | 1,000 / 10,000 / 100,000 | 5,000 / 50,000 / 500,000 |
| Password-protected share links | ✓ | ✓ | ✓ |
| Max share-link expiry | 30 days | 1 year | Unlimited |
| Support | Community | Email | Priority |
| (Later) team seats / SSO | — | — | ✓ |

Numbers are a **starting proposal** — every value is config-driven (below), so tuning them is an ops
change, not a redeploy. Free deliberately mirrors the current `buddydrop.storage.*` /
`buddydrop.limits.*` values so the first phase is a no-op for existing users.

## 2. What a plan gates

A plan is just a bundle of **allowances**:

- `storageBytes` — total stored bytes (already enforced in `FileService`).
- `maxUploadBytes` — largest single file.
- `upload` / `download` rate quotas — the hour/day/month windows already enforced by
  `UsageLimitService`.
- `maxShareExpiryDays` — caps what the share dialog may request.
- feature flags (password links today; SSO/seats later).

## 3. Where it plugs into the current code

Today quota and limits are read straight from global config:
`AppProperties.Storage.quotaBytes`, `AppProperties.Limits.{upload,download}`. The change is to route
those reads through the **user's plan**.

Introduce one seam:

```
PlanService.allowancesFor(UUID userId) -> Allowances
```

- `Allowances` carries the fields in §2.
- Every quota/limit decision calls this instead of reading global config directly:
  - `FileService.usage` → `allowances.storageBytes()`
  - `FileService.initiateUpload` → `allowances.maxUploadBytes()`
  - `UsageLimitService.quotaFor(user, kind)` → `allowances.upload()/download()`
  - `ShareService.share` → reject `expiresInDays > allowances.maxShareExpiryDays()`

This centralizes plan logic in one place and is **non-breaking**: `FREE`'s allowances are the current
config values, so nothing changes until a user's plan differs.

### Plan catalog = config, not hardcode

Define the catalog as `@ConfigurationProperties` so limits stay externally tunable (consistent with
how everything else in this app is configured):

```yaml
buddydrop:
  plans:
    free:      { storage-bytes: 10737418240, max-upload-bytes: 5368709120,
                 upload: {hourly: 50, daily: 500, monthly: 5000},
                 download: {hourly: 200, daily: 2000, monthly: 20000},
                 max-share-expiry-days: 30 }
    pro:       { ... }
    business:  { ... }
```

A `Plan` enum (`FREE`, `PRO`, `BUSINESS`) is just the key; the values live in config.

## 4. Data model

Keep billing out of `app_user`; model the subscription separately so the user row stays about
identity:

```
app_user            + plan varchar(16) not null default 'FREE'   -- fast path for enforcement
subscription        (Phase 3)
  user_id           uuid unique -> app_user(id)
  plan              varchar(16)         -- FREE | PRO | BUSINESS
  status            varchar(16)         -- ACTIVE | PAST_DUE | CANCELED
  stripe_customer_id     varchar
  stripe_subscription_id varchar
  current_period_end     timestamptz
  updated_at             timestamptz
```

`app_user.plan` is the denormalized value enforcement reads on every request; the `subscription`
table is the billing source of truth that keeps it in sync (Phase 3). Until then, `app_user.plan` is
set manually.

## 5. Rollout — independently shippable phases

**Phase 1 — model the plan (no billing, zero behavior change).**
`Plan` enum + `PlanCatalog` config + Flyway `V3` adding `app_user.plan` default `FREE`. Route all
quota/limit reads through `PlanService.allowancesFor`. Profile page shows the real plan + its
allowances (the page is already structured for this). Ship it — everyone is Free, nothing changes.

**Phase 2 — manual upgrades.**
A `/pricing` page listing tiers, and an admin path (`PATCH /api/admin/users/{id}/plan`, guarded) to
set a plan for early customers before billing exists. Profile gets an **Upgrade** CTA.

**Phase 3 — Stripe billing.**
Stripe Checkout for upgrade, Customer Portal for manage/cancel. Add the `subscription` table + a
signed webhook endpoint `/api/billing/webhook` handling `checkout.session.completed`,
`customer.subscription.updated`/`deleted`, `invoice.payment_failed` → update `subscription` + mirror
to `app_user.plan`. Downgrade/cancel reverts to `FREE` at `current_period_end` (grace period).

**Phase 4 — polish.**
Usage warnings at 80 % / 100 % (dashboard banner + email), annual billing & coupons, Business team
seats and SSO.

## 6. Edge cases to handle

- **Downgrade while over quota** — never delete files. Block new uploads until usage is back under
  the new quota; downloads and deletes still work. Surface it clearly on the dashboard.
- **Mid-window plan change** — rolling windows immediately use the new plan's limits (no reset dance).
- **Public share downloads** — already counted against the file owner's plan download limits.
- **Failed payment** — `PAST_DUE` keeps access briefly, then falls back to `FREE` enforcement.

## 7. Why this shape

- **One seam (`PlanService`)** instead of scattering `if (plan == …)` across services — matches the
  existing `StorageService` / `EmailSender` abstraction style.
- **Config-driven catalog** — same philosophy as the rest of the app; tune tiers without a redeploy.
- **Non-breaking first phase** — Free ≡ current defaults means Phase 1 is safe to ship immediately,
  and later phases layer on top.
