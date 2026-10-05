# Toolz News Architecture

Remote announcements for the Toolz Android app, authored in the toolz-website
admin panel (`/admin/news`) and delivered through Upstash Redis + Vercel
serverless functions. This file is verified line-by-line from code; every
section names the source file.

Contents:

1. Overview and honesty notes
2. Data model and targeting semantics
3. Backend (toolz-website)
4. Admin panel (`/admin/news` SPA)
5. Android data layer
6. Eligibility engine
7. Popup, history, dashboard, settings UI
8. Notifications and workers
9. App wiring (nav, loading, manifest)
10. Security model
11. Failure modes and stability
12. Tests
13. Admin cookbook
14. File inventory

## 1. Overview and honesty notes

The flow is one-directional: admin publishes in the website panel → JSON lives
in Redis → the app polls a public feed (boot, every 6 h via WorkManager,
dashboard foreground version check, one-shot wake at scheduled transitions,
manual refresh) → a local eligibility engine decides what pops up, what
notifies, and what lands in history. There is no push channel and no
per-device targeting; "targeting" means app-version rules evaluated on both
ends.

Corrections and limits worth knowing up front:

- Unpublish/archive propagation is best-effort reconciliation, not instant.
  `NewsRepository.syncIfStale()` archives locally any cached `published` item
  that is still time-valid and version-eligible for this device but absent from
  a non-empty feed. Empty or degraded feeds never trigger reconciliation (an
  empty feed is indistinguishable from a failed fetch; true deletes arrive as
  tombstones), and a degraded feed aborts the sync without touching the DB or
  the retry timestamp. Items targeting other versions are never touched.
- Propagation is version-driven, not window-driven: every admin mutation bumps
  the `news:version` generation counter (served as feed `v` and by the tiny
  `GET /api/news-version`, CDN-cached 60 s, with `ETag` support on both).
  The app compares it via
  `syncIfChanged()` on dashboard foreground and in the 6 h worker, force-syncing
  only when the generation moved — deletes and edits land within minutes with
  no manual refresh. The 6 h stale gate remains as a backstop.
- Scheduled publish/expiry flips no counter, so both feed surfaces also serve
  `nextTransitionAt` (earliest future `publishAt`/`expiresAt`). The website
  refetches when it passes; the app persists it (`news_next_transition`) and
  enqueues a one-shot `NewsTransition` worker (+30 s slack) plus a due-check
  in `syncIfChanged()`. Horizon capped at 30 days; failures never break sync.
- The public feed is CDN-cached for ~1 minute (`s-maxage=60`), so a publish
  takes about a minute to reach devices (and the website home section / `/news`
  page). The app additionally syncs on version change (see above), so the 6 h
  stale gate only matters if the version check itself fails (forced refresh
  bypasses everything).
- `GET /api/news?all=1` skips version filtering for the public website surface
  (home previews, `/news` page). Status + time window always apply, so drafts
  and scheduled/expired items never leak to the public page.
- `notify: false` gates system notifications only; popups ignore it. A
  `critical` item bypasses both user toggles (popup and notification) by
  design, with an in-app explainer.
- No foreground delay exists: popups show immediately on the dashboard as soon
  as an eligible item is present (the old `delaySeconds` field is accepted on
  the wire for back-compat but ignored everywhere and hidden from the admin).
- Disappearing news = `disappearing: true` + `expiresAt`: the feed drops it at
  expiry like everything else, and the next device sync hard-deletes it from
  Room (history included). Non-disappearing expired items stay in history
  (90-day prune, or immediate prune when `showInHistory` is false).
- `requiresAction` is enforced on-device (`toEntity` coerces it to
  `priority == "critical"`); the server stores whatever validates.
- History shows everything cached locally, including expired and locally
  archived items (pruned only 90 days after expiry, or immediately if
  `showInHistory` is false).
- News images must be `https://`. `toolz://` deep links are allowed for action
  buttons only and open via a generic `ACTION_VIEW` intent.

## 2. Data model and targeting semantics

Canonical shape (v1, `schemaVersion: 1`). Server validation lives in
`toolz-website/api/news-admin.ts` (`validateItem`); the SPA mirrors it in
`toolz-website/src/lib/news-schema.ts` (zod); Android parses it in
`app/src/main/java/com/frerox/toolz/data/news/NewsDto.kt` (kotlinx,
`ignoreUnknownKeys`).

| Field | Type / limits | Meaning |
|---|---|---|
| `id` | 12-char alphanumerics (server-generated) | Stable key: Redis, Room PK, notification ID seed |
| `title` | 3–120 chars | Popup/history headline |
| `body` | 1–2000 chars, HTML stripped | Advanced markdown (see below) |
| `imageUrl` | https only, ≤500 chars, host-allowlisted | Optional cover, natural ratio (capped client-side) |
| `actionLabel` / `actionUrl` | label ≤50 chars, URL ≤500 chars, https or `toolz://` | Custom CTA button text + target |
| `priority` | `info`/`feature`/`fix`/`promo`/`critical` | Chip color, sort boost, bypass key |
| `status` | `draft`/`published`/`archived` | Only `published` is served/shown as popup |
| `pinned` | bool | Sorts first in feed and history |
| `publishAt` / `expiresAt` | ISO or null (= now / never) | Visibility window, checked on both ends |
| `min/maxAppVersion` | semver or null | Range gate (`1.1` ≡ `1.1.0`, pre-release suffix ignored) |
| `onlyVersions` / `excludedVersions` | ≤30 entries each | Allowlist (empty = all) / denylist (wins) |
| `frequency` | `once`/`every_launch`/`daily`/`weekly`/`interval` | Recurrence cap |
| `intervalHours` | 1–720 | Only for `interval` |
| `maxImpressions` | 1–100 or null | Total popup count per device (`once` implies 1) |
| `dismissible` | bool (default true) | ✕ button, scrim/back dismissal, "Later" snooze |
| `showInHistory` | bool (default true) | If false, pruned at expiry instead of kept |
| `disappearing` | bool (default false) | Auto-deleted from devices at `expiresAt` (popups, notifications AND history) |
| `requiresAction` | bool | Blocking dialog instead of bottom sheet (critical only) |
| `notify` | bool (default true) | Also post a system notification |

Payload caps: 8 KB per item (server-enforced), ≤50 published items stored,
≤20 per feed response.

Markdown support (same syntax everywhere — app popup/history, `/news` page,
admin live preview, all verified against the renderers):

- Headings (`##`), **bold**, *italic*, `inline code`, links, images-alt text
- Bullet, ordered and checkbox lists, blockquotes, fenced code blocks,
  horizontal rules, tables (app + website page)
- App rendering is the shared `MarkdownContent` (`ui/components/`),
  popup at 17 sp / 12 dp rhythm, history expanded at 16 sp / 10 dp, links open
  in the browser, tables keep raw-markdown cells. Headers parse inline
  markdown like every other block.
- Website rendering is the shared `src/components/news/NewsBody.tsx`
  (used by `/news` and the admin preview); home teaser cards use
  `src/lib/stripMarkdown.ts` plain-text fallback (tested in
  `src/test/news-body.test.ts`)
- Raw HTML is never injected on any surface (server strips tags, website uses
  React nodes, Compose has no HTML path)

## 3. Backend (toolz-website)

`toolz-website/api/news.ts` — `GET /api/news?appVersion=1.1.6&platform=android`
(`vercel.json`: `maxDuration` 10):

- Public CORS (`*`), `GET`/`OPTIONS` only. Cache headers carry an explicit
  browser `max-age=60` alongside `s-maxage=60`: without it browsers may
  heuristically cache an empty feed far beyond `s-maxage` and `/news` looks
  permanently empty. The website hook additionally fetches with
  `cache: 'no-store'` (tiny payload, freshness wins). `ETag` (`W/"v…"` ) +
  `If-None-Match` → `304` saves mobile data; fail-open per-IP fixed-window
  throttle (120 req/min, Redis-backed, `429` + `Retry-After`).
- Reads `news:index` (ZSET, score = publish time), pipelined `MGET` of up to
  100 items, filters `published` + time window + version eligibility (`all=1`
  skips the version gate for the website surface), sorts pinned-first then
  newest, slices 20. `removedIds` carries up
  to 200 recent deletion tombstones for device cache eviction.
  `nextTransitionAt` carries the earliest future `publishAt`/`expiresAt`
  (null when none) so scheduled flips need no mutation. Tombstone keys are
  read via non-blocking `SCAN` (KEYS fallback, 500 cap).
- Never fails the caller: missing Redis config, Redis outage, or any exception
  returns `200 { count: 0, news: [], degraded: true }`.
- `?preview=1` bypasses CDN caching (public data, no auth — the name is a
  cache hint, not a privilege).
- `GET /api/news-rss` (same rules, RSS 2.0, `s-maxage=300`) powers the `/news`
  RSS button with per-item `#news-<id>` links and image enclosures.

`toolz-website/api/news-admin.ts` — same-origin JSON API
(`vercel.json`: `maxDuration` 15). Actions via `?action=`:
`login`, `logout`, `list`, `create`, `update`, `publish`, `unpublish`,
`archive`, `delete`, `audit`, `feed-health`, `repair-index`. Images upload
through the separate `api/news-image.ts` proxy (`maxDuration` 30,
`IMGBB_API_KEY` stays server-side, 5 MB cap, host-verified `i.ibb.co`
response). Login takes `{ password }` in the
JSON body only (`?pw=` in the URL is rejected; `?password=` is ignored).

`feed-health` (authed GET) returns `{ indexSize, payloadCount, liveCount,
feedVersion, nextTransitionAt, orphanIds, items: [{ id, title, status, publishAt, expiresAt,
liveOnPublicFeed, reason }] }` using the exact feed rules (status + time
window; version targeting is per-device). `reason` is `live`, `draft (not
published)`, `archived`, `scheduled at <iso>`, `expired at <iso>`, and index
members without payloads are reported as `orphanIds`.
`repair-index` (authed POST) deletes orphan index members and returns
`{ removed, orphans }`. Audit/tombstone key reads use non-blocking `SCAN`
(KEYS fallback). `list` accepts optional `?limit=&offset=` (defaults to all,
backward compatible). Session cookies omit `Secure` on localhost/http so dev
login works; production keeps `HttpOnly; Secure; SameSite=Lax`.
`create`/`update`/`publish`/`unpublish`/`archive` responses include
`visibility` (same verdict shape for the single saved item) so the admin can
confirm "Saved — LIVE" vs "Saved — NOT live: <reason>" at save time.
Delete writes a 90-day tombstone; devices hard-delete the cached copy
(popups, notifications, history) on their next sync.

Redis schema (shares the instance with the specs backend, no key overlap):
- `news:item:<id>` — item JSON, no TTL (lifecycle via status/dates)
- `news:index` — ZSET member `<id>`, score = publish epoch
- `news:config` — optional `{ allowedHosts: [] }` merged over built-ins
- `news:audit:<ts>:<rand>` — `{ ts, ipHash, action, id, title }`, 90-day TTL, capped
  at 500 entries
- `news:tombstone:<id>` — deletion timestamp, 90-day TTL; served as feed
  `removedIds` so devices hard-delete cached copies on next sync
- `news_admin_session:<tokenId>` — `{ csrf }`, 6 h TTL
- `newsfails:<ip>` / `newsban:<ip>` — 5-strike lockout, 15-min window/ban

Patch semantics: any key present in an update patch (including explicit
`null`/empty) is applied — clearing a field in the editor really clears it.
`publish`/`unpublish`/`archive` are status-only shortcuts.

## 4. Admin panel (`/admin/news` SPA)

Route registered in `toolz-website/src/App.tsx` (above the catch-all, no public
nav link). M3 Expressive styling (32 px cards, pill buttons) on Tailwind +
shadcn, matching the `reset.ts` admin language.

- `src/pages/AdminNews.tsx` — login gate → dashboard (published/draft counts,
  filter tabs All/Published/Drafts/Archived/Expired, search, refresh,
  "Publish all drafts" bulk action with confirm), audit
  section, editor host. Restores a valid session on page refresh (cookie-only
  `list` probe, "Checking session…" state) instead of forcing re-login.
- `src/hooks/useNewsAdmin.ts` — `credentials: include` fetch wrapper, CSRF kept
  in React state (never localStorage), `login/logout/refresh/mutate`.
- `src/components/news-admin/NewsLoginCard.tsx` — password form with
  show/hide and lockout messaging.
- `src/components/news-admin/NewsListTable.tsx` — status/priority pills,
  targeting summary (`describeTargeting`), LIVE-on-/news vs reason badges
  (shared verdict), Clone, Publish/Unpublish/Archive/Delete.
- `src/components/news-admin/NewsEditorDialog.tsx` — tabbed (Content with
  templates, counters, markdown toolbar; Targeting with Everyone reset,
  Latest-version shortcut, who-sees-this + version simulator; Behavior with
  recurrence presets, plain-language toggles, schedule quick chips (local-time
  labels with UTC storage note) and a live
  verdict line ("~1 min"); triple Preview: popup + website article), status segmented
  control with consequence captions, per-tab zod error counts with auto-jump,
  Cancel / Save draft / Publish now (Update/Unpublish when editing),
  unsaved-changes confirm. "Publish now" forces `published` + immediate.
  Every save auto-verifies against the live feed (`compareSavedVsFeed`) and
  reports "verified live" vs the exact mismatch.
- `src/components/news-admin/NewsFeedCheck.tsx` — auto-runs on mount (no more
  manual "Run feed check" needed): index/payload/live counts, generation,
  next-transition badge, orphan IDs, per-item verdicts.
- `src/components/news-admin/NewsPreviewCard.tsx` — popup mock (priority chip,
  frequency caption, markdown body, CTA row).
- `src/components/news-admin/NewsAuditLog.tsx` — filter chips (All/Content/
  Publishes/Deletes/Access), relative timestamps, colored action badges,
  titles inline, expandable raw detail (id, hashed actor, full time).
- `src/lib/news-schema.ts` — zod schema, defaults, `compareVersions`,
  `versionEligible`, `describeTargeting`. Covered by
  `src/test/news-schema.test.ts` (vitest, 4 tests).

Public website surface (same feed, `?all=1`, no login):

- `src/hooks/usePublicNews.ts` — client fetch of `/api/news?all=1` with
  module-level shared cache (home + `/news` fetch once), `ETag`-friendly
  `no-store` fetch, 60 s `news-version` poll that refetches only on generation
  or transition-hint change (plus due-check past `nextTransitionAt`),
  loading state, empty-on-failure, plus `formatNewsDate`. Distinguishes feed
  failure (`unavailable`, from `degraded`/HTTP/parse errors) from a genuinely
  empty feed so `/news` can be honest about breakage.
- `src/lib/newsVerdict.ts` — single `getVisibilityVerdict` implementation of
  the public-feed rules (status + time window) plus `describeWhen` relative
  times; used by the editor verdict line and list badges (tested in
  `src/test/news-verdict.test.ts`).
- `src/pages/News.tsx` — `/news` route: M3 hero, priority filter chips
  (all/critical/feature/fix/promo/info) + RSS button (`/api/news-rss`),
  per-item `#news-<id>` anchors with copy-link + scroll-to-hash deep-link,
  full cards (image, advanced markdown
  body, date, CTA), `toolz://` actions render as "in-app only" hints (no broken
  new-tab), skeleton/empty states, plus a distinct "feed unavailable +
  Retry" state when `unavailable`, Navbar + Footer + download dialog.
- `src/components/landing/NewsSection.tsx` — home `/#news` teaser: up to 3
  latest real cards + "All news" entry to `/news`. Renders nothing while the
  feed is empty/unreachable so the home page stays clean. Mounted in
  `src/pages/Index.tsx` between HowItWorks and CTA; linked from
  `src/components/landing/Navbar.tsx` ("News" entry, desktop + mobile).

## 5. Android data layer

`app/src/main/java/com/frerox/toolz/data/news/`:

- `NewsConstants.kt` — `WEBSITE_BASE_URL` / `WEBSITE_NEWS_URL` (`/news` page link
  used by the popup footer).
- `NewsDto.kt` — `@Serializable` feed/item DTOs (`nextTransitionAt` additive,
  defaults keep old servers/clients compatible), unknown-field tolerant.
- `NewsApi.kt` — `GET api/news?appVersion=&platform=`, `GET api/news-version`.
- `NewsVersion.kt` — pure semver compare + eligibility (range/only/excluded).
  Covered by `NewsVersionTest` (3 tests).
- `NewsEntity.kt` — `news_items` Room entity (versions as CSV, epochs as
  millis), `toEntity` sanitizer (length caps, `requiresAction`
  critical-only coercion), `NewsDao` (upsert, `LIMIT/OFFSET` paging,
  `publishedOrdered` + lightweight `publishedIds` for the unread fast-path,
  `markArchived`, prune incl. disappearing auto-delete).
- `NewsDatabase.kt` — separate plain Room DB (`toolz_news_db`, v2 with
  `NEWS_MIGRATION_1_2` adding `disappearing`, `exportSchema = false`). Deliberately separate from `AppDatabase` (v61,
  SQLCipher, strict migration chain) so news never risks user data on schema
  drift. News is public content; no encryption needed.
- `NewsRepository.kt` — `syncIfStale` (6 h stale gate, **single-flight mutex**
  so boot/dashboard/worker/manual triggers share one sync), `syncIfChanged`
  (cheap generation check → force-sync on change + transition-due check, stale
  fallback), tombstone deletes also cancel shade rows, `nextTransitionAt`
  persist + one-shot `NewsTransition` worker, unpublish
  reconciliation, prune (incl. disappearing auto-delete), `syncAndNotify`
  (sync + immediate notify up to 3 new items, once-per-id), impression/snooze
  state, `popupCandidate`, `notificationCandidates(limit)`.
- `NewsNotifier.kt` — posts per-item notifications (stable IDs in the
  8100–8899 band, content PendingIntent codes 28100–28899 + dismiss 29100–29899
  (800-wide, non-overlapping, so 100+ items never alias), View +
  Dismiss actions, BigText style) and `cancel()` used by the dismiss receiver
  and tombstone eviction.

`di/NewsModule.kt` provides the Retrofit API (same
`https://toolz-app.vercel.app/` base and kotlinx converter as the device-specs
API), the database, and the DAO.

`data/settings/SettingsRepository.kt` additions (all default **ON** unless
noted): `news_enabled`, `news_notifications_enabled`, `news_last_sync`,
`news_feed_version`, `news_next_transition`,
`news_impressions_json`, `news_last_shown_json`, `news_dismissed_ids`,
`news_snoozed_until_json`, `news_seen_ids`, `news_notified_ids`, with flows
and capped setters (200/300-entry caps).

## 6. Eligibility engine

`NewsRepository.eligibleItems()` applies, in order: `notify` is NOT a popup
gate (notifications only) → time window → version eligibility → master toggle
(`critical` bypasses) → dismissed → snoozed-until → impression cap →
frequency cooldown (`once` = zero impressions; `every_launch` = once per
process via `NewsViewModel.sessionPopupShown`; `daily`/`weekly`/`interval` =
hours since last shown). Sort: critical → pinned → newest; head is the popup,
top-3 feed the worker. Every stage is exception-isolated: failures yield no
popup, never a crash.

## 7. Popup, history, dashboard, settings UI

`ui/screens/news/`:

- `NewsPopup.kt` — `ModalBottomSheet` (28 px top radius, drag handle) with
  priority chip, pin marker, natural-ratio Coil cover (capped 420 dp, 20 dp
  corners), full `MarkdownContent` body at 17 sp / 12 dp rhythm, CTA + Later + ✕ + a footer row ("View all news →"
  opens in-app history, "Read on website ↗" opens
  `NewsConstants.WEBSITE_NEWS_URL#news-<id>` in the browser).
  `requiresAction` + `critical` renders a blocking `AlertDialog` instead. The (i) button appears only on critical
  items shown while the master toggle is off and explains the bypass.
  Non-dismissible items without a CTA can only be left via "View all" — an
  accepted config constraint documented for admins (section 13).
- `ToolzNewsScreen.kt` — backup-style rounded (32 dp) `ExpressiveTopAppBar`
  with subtitle + refresh action, `toolzBackground`, fading list edges, full
  history: search field + priority filter chips (parity with `/news`) + Android
  13+ "Notifications off → Enable" card (one-tap `POST_NOTIFICATIONS` request
  with settings fallback), `LazyColumn` with keyed cards, 10-by-10 pages, collapsed cards show
  plain text (`stripMarkdown`, no raw syntax before "Read more"), expanded
  cards full markdown at 16 sp, natural-ratio images (capped 360 dp, 20 dp
  corners), Share action (title + `#news-<id>` link), real unread dots via `seenIds`, notification deep-link
  scroll-to-highlight, loading/empty/error states, refresh button.
- `NewsViewModel.kt` — immediate popup on dashboard arrival (no delay),
  session guard, foreground `checkNotifications` (sync + immediate notify),
  history paging, badge counts, settings toggles, `syncNow`.
- `NewsPreviews.kt` — `@Preview` history cards + 4 popup variants (standard,
  long-markdown, critical-blocking, dark).

Entry points: dashboard header news button with unread badge beside the
offline/performance toggles (`DashboardScreen.kt`: `DashboardHeader` +
threading through `DashboardContent`/`HomeTabContent`, popup host with
1-per-session guard); Settings → NEWS section (master + notification toggles,
critical explainer dialog, history entry with unread count, manual sync) and
an About-card "Toolz News" button (`SettingsScreen.kt`).

## 8. Notifications and workers

`worker/NewsCheckWorker.kt` (Hilt, 6 h periodic (`UPDATE` so existing installs
migrate), `CONNECTED` + battery-not-low,
unique name `NewsCheck`, scheduled in `MainActivity.scheduleNewsCheck()`):
syncs, then notifies up to 3 newly-eligible `notify` items (each ID exactly
once via `notified_ids`; `critical` bypasses the notifications toggle).
A one-shot `NewsTransition` work (same worker class, `REPLACE`) wakes at
`nextTransitionAt` for scheduled flips.
`worker/NewsActionReceiver.kt` (manifest-registered, `exported=false`) marks
dismissed AND cancels the shade row via the same stable ID.
`util/NotificationHelper.kt` gains `CHANNEL_TOOLZ_NEWS` ("Toolz News",
`IMPORTANCE_DEFAULT`) and `ID_NEWS_BASE` (8100). Taps deep-link to
`toolz_news` with `news_id` highlight. Android 13+ `POST_NOTIFICATIONS` is
requested via the in-app "Notifications off → Enable" card (history screen);
denial still fails silently by design, with a settings fallback.

## 9. App wiring

- `ui/navigation/Screen.kt`: `Screen.ToolzNews("toolz_news")`; route in
  `ToolzNavHost` (`MainActivity.kt`) with highlight passthrough; the generic
  `navigate_to` intent extra resolves it with no resolver change;
  `pendingNewsId` clears when leaving the route.
- `LoadingViewModel.kt`: 8 s-capped news sync as stage 3.2 plus fire-and-forget
  sync on the 5-minute fast path — boot never blocks on news.
- Dashboard evaluates the popup on first composition (no delay — immediate).
- `AndroidManifest.xml`: `NewsActionReceiver` entry.

## 10. Security model

Separate `NEWS_ADMIN_PASSWORD` (never `SYNC_PASSWORD`); SHA-256 hash-then-
`timingSafeEqual` (no length oracle); 6 h HMAC session cookie (`HttpOnly`,
`Secure`, `SameSite=Lax`); per-session CSRF required as header/body on all
mutating calls (query-string CSRF rejected); IP ban after 5 failures; strict
security headers; admin API same-origin (public feed alone is CORS `*`);
input validation server-side with allowlisted https hosts and IP-literal
rejection; bodies HTML-stripped; audit log with hashed IPs.

## 11. Failure modes and stability

Every repository/worker/sync path catches and degrades: Redis outage → empty
degraded feed; offline → Room cache serves popup + history; sync errors never
propagate to UI or boot; notification errors are swallowed; popup shows max
once per session; worker returns success (no retry storms). News DB is
isolated from the encrypted main DB.

## 12. Tests

- Website: `src/test/news-schema.test.ts` (validation, version compare,
  targeting), `src/test/news-body.test.ts` (`stripMarkdown`),
  `src/test/news-verdict.test.ts` (visibility verdicts) — `vitest run` green;
  `tsc -p tsconfig.app.json` and api-file `tsc` clean.
- Android: `app/src/test/.../news/NewsVersionTest` (ordering, short/pre-release
  equality, eligibility matrix) and `NewsDtoTest` (unknown-field tolerance) —
  `:app:testDebugUnitTest --tests "com.frerox.toolz.news.*"` green, full
  `:app:compileDebugKotlin` green (which also type-checks all UI/nav wiring).

## 13. Admin cookbook

1. Vercel env: `UPSTASH_REDIS_REST_URL`, `UPSTASH_REDIS_REST_TOKEN`,
   `NEWS_ADMIN_PASSWORD`, `IMGBB_API_KEY` (only needed for the editor Upload
   button). Redeploy after backend changes.
2. Open `/admin/news`, unlock, "+ New announcement". Empty version fields =
   everyone. Use `onlyVersions` for staged rollouts, `excludedVersions` for
   bad builds. Upload cover art with the Upload button (imgbb, downscaled
   automatically) or paste an `https://` URL.
3. Cadence: `once` for changelogs; `weekly` + `maxImpressions` for promos.
   There is no foreground delay — popups appear on the dashboard immediately.
4. "Disappear after" chips (1h/24h/7d/30d) set `expiresAt` + `disappearing`
   for self-cleaning announcements; without it, expired items stay in
   on-device history.
5. Go/no-go on `notify`: off for silent history drops; keep on for launches.
   `critical` + `requiresAction` for outages (bypasses user toggles — use
   sparingly, the app tells users why).
6. Unpublish/Archive removes the popup on next sync (minutes via version check
   or 6 h backstop, or manual refresh);
   the entry stays in on-device history. Delete removes it everywhere via
   tombstones (next sync + feed refresh, shade rows dismissed).
7. Constraints: images must be `https://` allowlisted hosts (or uploaded via
   the imgbb button); every item needs
   either a CTA or `dismissible` on (a non-dismissible item with no action can
   only be left via "View all news"); keep bodies short — 2000 chars max.
8. Locking out? Wait 15 min or clear `newsban:<ip>` / `newsfails:<ip>` in Redis.
   Activity is in the audit tab (90 days, hashed IPs).

## 14. File inventory

Website (new): `api/news.ts`, `api/news-admin.ts`, `api/news-image.ts`,
`api/news-version.ts`, `api/news-rss.ts`, `src/lib/news-schema.ts`,
`src/lib/newsVerdict.ts`, `src/lib/stripMarkdown.ts`,
`src/hooks/useNewsAdmin.ts`, `src/hooks/usePublicNews.ts`,
`src/components/news-admin/` (6 files), `src/components/landing/NewsSection.tsx`,
`src/components/news/NewsBody.tsx`,
`src/pages/AdminNews.tsx`, `src/pages/News.tsx`, `src/test/` (3 news test files),
`NEWS_ADMIN.md`.
Website (edited): `vercel.json`, `src/App.tsx`, `src/pages/Index.tsx`,
`src/components/landing/Navbar.tsx`.

Android (new): `data/news/` (8 files), `di/NewsModule.kt`,
`ui/screens/news/` (4 files), `worker/NewsCheckWorker.kt`,
`worker/NewsActionReceiver.kt`, `app/src/test/.../news/` (2 files).
Android (edited): `MainActivity.kt`, `DashboardScreen.kt`,
`SettingsScreen.kt`, `LoadingViewModel.kt`, `Screen.kt`,
`SettingsRepository.kt`, `NotificationHelper.kt`, `AndroidManifest.xml`.

## 15. Draft announcement — legacy Whisper token migration (publish via `/admin/news`, not in-app seed)

News items are authored in the `toolz-website` admin panel (`/admin/news`) and
served from Redis — the app never seeds them locally, so this is copy for the
admin to paste, plus the matching in-app banner strings
(`st_Whisper_Legacy_Migrate_*` in `app/src/main/res/values/strings.xml`).
In-app code only documents the payload here (`NewsRepository.kt` companion
comment); no behavior change.

Suggested payload:

- `title`: "Action needed: migrate your Whisper account"
- `body`: "Legacy Whisper token accounts (pre-2026 truncated emails) must
  migrate within 30d — open Whisper > log in once to auto-upgrade, then the
  old credential is disabled. Contact support if locked."
- `priority`: `critical`, `notify`: true, `frequency`: `once`,
  `requiresAction`: false (blocking dialog not needed; banner + login flow
  covers it), `actionLabel`: "Log in to migrate", `actionUrl`:
  `toolz://whisper/login` (deep link resolved by the generic `navigate_to`
  intent extra; falls back to opening Whisper).
- Targeting: no version gate (all versions), no expiry shorter than 30 days
  (`expiresAt` = publish + 30d, `disappearing`: false so it stays in history).
- In-app banner strings: `st_Whisper_Legacy_Migrate_Title` /
  `_Desc` / `_Action` mirror the copy above for the login-screen banner.
