# Arro-POS — Self-Hosted Sync, Cloud Availability & Notifications

**Status: planning document. Nothing described here is built yet.**
This is the brief for a future phase. The app today is single-device and
offline-only, and that is deliberate — read "Why offline-first is not
negotiable" before changing anything.

**Hosting decision (fixed): your own VPS or shared hosting, your own MySQL.**
No Firebase, no Supabase, no third-party backend-as-a-service. Every byte of
shop data lives on infrastructure you control. This document is written to that
constraint throughout.

Written for: whoever implements multi-device sync, whether that is a person or
an AI agent. It is meant to be handed over whole as a master prompt.

---

## 0. Context you must not lose

Arro-POS is an Android point-of-sale app for small Sri Lankan shops — groceries,
pharmacies, salons, hardware stores, repair counters. It is built around one
sentence: **Open → Sell → Pay → Print.**

Facts about the current build that constrain every decision below:

| Thing | Current state |
|---|---|
| Device storage | Room / SQLite. `kadepos_database`, currently **v7**, with real migrations |
| Network | **Offline-first.** Selling never needs a network. An optional, provider-controlled per-device Google Drive backup/sync can upload local snapshots; Drive is never on the selling path |
| Printing | Real Bluetooth SPP + Wi-Fi TCP (port 9100) ESC/POS |
| Identity | Per-staff 4-digit PIN. No accounts, no email, no server |
| Roles | 19 permissions, 4 roles, per-person overrides as CSV on the staff row |
| Money | LKR only, `Rs.` prefix |
| Migrations | **Real migrations, v1→v7.** No destructive fallback. Schemas exported to `app/schemas` |
| Entities | 18 (see appendix) |
| Optional features | Stock counting, credit book, cash drawer count, staff — each independently switchable |

### Why offline-first is not negotiable

The typical shop has one Android phone, patchy mobile data, and daily power
cuts. A sale must complete in under two seconds with aeroplane mode on. If the
server is ever on the critical path of taking money from a customer, the
product has failed.

**The test to apply to any design:** turn off all networking, sell twenty items,
take cash, print. If anything blocks, spins, or errors, the design is wrong.

### Two databases, two jobs — do not confuse them

| | On the phone | On your server |
|---|---|---|
| Engine | **SQLite** (via Room) | **MySQL 8.0+** (or MariaDB 10.6+) |
| Role | The source of truth for that device | The meeting point between devices |
| Availability | Always | Best effort |

They are **not** the same schema and should not be generated from one another.
SQLite is loose about types; MySQL is strict. Keep the mapping explicit and
written down (§3.4).

---

## 1. Target architecture

```
   Device A (counter)        Device B (stock room)     Device C (owner phone)
   ┌────────────────┐        ┌────────────────┐        ┌────────────────┐
   │  Compose UI    │        │  Compose UI    │        │  Compose UI    │
   │  PosViewModel  │        │       ...      │        │       ...      │
   │  PosRepository │        │                │        │                │
   │  Room / SQLite │        │                │        │                │
   │  Outbox table  │        │                │        │                │
   └───────┬────────┘        └───────┬────────┘        └───────┬────────┘
           │      SyncWorker — HTTPS, opportunistic            │
           └──────────────────┬───────────────────────────────┘
                              ▼
              ┌───────────────────────────────────┐
              │  YOUR VPS / shared host           │
              │  ┌─────────────────────────────┐  │
              │  │ PHP 8.2 or Node — sync API  │  │
              │  ├─────────────────────────────┤  │
              │  │ MySQL 8 — one DB, many shops│  │
              │  └─────────────────────────────┘  │
              └───────────────────────────────────┘
```

### The non-negotiable rules

1. **No UI code path may await a network call.** Ever.
2. **Writes go to Room first**, then to an outbox. Sync drains the outbox later.
3. **A sale is immutable once completed.** It syncs as an append.
4. **Sync is per shop.** A shop is the tenant boundary.
5. **Turning sync off must leave a fully working app.**

---

## 2. Choosing your hosting

You said VPS or shared hosting. They are very different for this workload.

| | Shared hosting (cPanel) | VPS (2 GB, e.g. Hetzner/DO/Contabo) |
|---|---|---|
| Cost | ~$3–8/mo | ~$5–12/mo |
| MySQL | Provided, often `max_connections` 25–50 | Yours, tune freely |
| Long-running processes | **Usually forbidden** | Fine |
| Cron granularity | Often 5–15 min minimum | Per minute or systemd timers |
| PHP | Always available | Your choice |
| Node/Java | Rarely | Fine |
| Backups | Host's, often weekly | Yours, scriptable |
| Suits | Up to ~50 shops | Hundreds |

**Recommendation: start on shared hosting with PHP, move to a VPS at scale.**

The sync API is a handful of stateless HTTPS endpoints doing simple MySQL
reads and writes. Plain **PHP 8.2 + PDO** runs anywhere, needs no build step,
no process manager, and no Docker. That is a genuine advantage when the thing
must keep running for years with minimal attention.

Pick a VPS from the start **only if** you already prefer Node/Kotlin on the
server, or you expect more than ~50 shops soon.

**Do not use SQLite on the server.** It is superb on the phone and wrong for a
multi-writer network service — concurrent writes from several shops will hit
`SQLITE_BUSY`. MySQL is the right choice server-side.

### Sizing

A shop doing 300 sales/day with 3 devices produces roughly:
- ~1,200 rows/day (sales + items + stock movements)
- ~35,000 rows/month
- ~5 MB/month of MySQL storage

100 shops ≈ 3.5M rows/month ≈ 500 MB/month. A 2 GB VPS handles this comfortably
for a couple of years with the archival policy in §6.

---

## 3. Data model changes

### 3.1 Every syncable table gains

```kotlin
val uuid: String = java.util.UUID.randomUUID().toString(),  // sync identity
val shopId: String = "",           // tenant key
val updatedAt: Long = 0L,          // device clock, last local edit
val serverUpdatedAt: Long = 0L,    // server clock, last confirmed sync
val syncState: String = "PENDING", // PENDING, SYNCED, CONFLICT, FAILED
val deletedAt: Long? = null,       // soft delete; never hard-delete a synced row
val originDevice: String = ""
```

**Use UUIDs, not autoincrement, as the sync identity.** Two devices offline
will both mint `id = 47`. Generate a UUID on the device; the server treats it
as the natural key. This single decision removes an entire class of merge bug.

Keep Room's existing `Long` primary keys for local relations — changing them is
invasive — but add `uuid` as a `UNIQUE` indexed column and sync on that.

### 3.2 The outbox

```kotlin
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["nextAttemptAt"]), Index(value = ["entityUuid"])]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityTable: String,
    val entityUuid: String,
    val operation: String,        // INSERT, UPDATE, DELETE
    val payloadJson: String,      // the full row, so replay needs no lookup
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String = "",
    val nextAttemptAt: Long = 0L
)
```

Write to `sync_outbox` in the **same Room transaction** as the business write.
If the app dies between the two you get a silent divergence nobody notices for
weeks.

### 3.3 Sync metadata

```kotlin
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val shopId: String = "",
    val deviceId: String = "",
    val deviceName: String = "",        // "Front counter"
    val serverUrl: String = "",         // the owner's own server
    val cloudEnabled: Boolean = false,
    val lastPullAt: Long = 0L,
    val lastPushAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val pullCursor: Long = 0L,          // server watermark
    val pendingCount: Int = 0,
    val lastError: String = ""
)
```

### 3.4 MySQL schema

One database, all shops, `shop_id` on every row. Do **not** create a database
per shop — shared hosting caps how many you may have, and migrations become
unmanageable.

```sql
CREATE DATABASE kadepos
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

`utf8mb4` is required, not optional: Sinhala and Tamil product names break on
`utf8`.

```sql
CREATE TABLE shops (
  id            CHAR(36)     NOT NULL PRIMARY KEY,
  name          VARCHAR(200) NOT NULL,
  phone         VARCHAR(30),
  plan          VARCHAR(20)  NOT NULL DEFAULT 'FREE',
  row_quota     INT          NOT NULL DEFAULT 50000,
  rows_used     INT          NOT NULL DEFAULT 0,
  device_quota  TINYINT      NOT NULL DEFAULT 3,
  created_at    BIGINT       NOT NULL,
  archived_at   BIGINT       NULL
) ENGINE=InnoDB;

CREATE TABLE devices (
  id            CHAR(36)     NOT NULL PRIMARY KEY,
  shop_id       CHAR(36)     NOT NULL,
  name          VARCHAR(100) NOT NULL,
  token_hash    CHAR(64)     NOT NULL,          -- SHA-256, never the raw token
  approved      TINYINT(1)   NOT NULL DEFAULT 0,
  last_seen_at  BIGINT       NULL,
  revoked_at    BIGINT       NULL,
  INDEX idx_shop (shop_id),
  UNIQUE KEY uniq_token (token_hash),
  FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Pattern every synced table follows.
CREATE TABLE sales (
  uuid              CHAR(36)     NOT NULL PRIMARY KEY,
  shop_id           CHAR(36)     NOT NULL,
  invoice_number    VARCHAR(40)  NOT NULL,
  customer_uuid     CHAR(36)     NULL,
  customer_name     VARCHAR(200) NOT NULL DEFAULT 'Walk-in',
  cashier_name      VARCHAR(200) NOT NULL DEFAULT '',
  sold_at           BIGINT       NOT NULL,       -- the sale's own timestamp
  subtotal          DECIMAL(12,2) NOT NULL,
  discount_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_amount      DECIMAL(12,2) NOT NULL,
  payment_method    VARCHAR(20)  NOT NULL,
  cash_received     DECIMAL(12,2) NOT NULL DEFAULT 0,
  change_given      DECIMAL(12,2) NOT NULL DEFAULT 0,
  status            VARCHAR(24)  NOT NULL DEFAULT 'COMPLETED',
  origin_device     CHAR(36)     NOT NULL,
  updated_at        BIGINT       NOT NULL,       -- device clock
  server_seq        BIGINT       NOT NULL,       -- server watermark, see below
  deleted_at        BIGINT       NULL,
  INDEX idx_pull (shop_id, server_seq),
  INDEX idx_invoice (shop_id, invoice_number),
  FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

**`DECIMAL(12,2)` for every money column. Never `FLOAT` or `DOUBLE`.** Binary
floating point cannot represent `0.10` exactly; a day of sales will drift by
cents and the owner will notice, because reconciling cash is the one thing they
check by hand. Room stores these as `Double` — convert at the boundary and
round to 2 dp on the way in.

**`server_seq`, not a timestamp, drives incremental pull.** Use a single
monotonic counter per shop:

```sql
CREATE TABLE shop_sequence (
  shop_id  CHAR(36) NOT NULL PRIMARY KEY,
  seq      BIGINT   NOT NULL DEFAULT 0
) ENGINE=InnoDB;
```

Allocate inside the same transaction as the write:

```sql
UPDATE shop_sequence SET seq = seq + 1 WHERE shop_id = ?;
SELECT seq FROM shop_sequence WHERE shop_id = ?;
```

Device clocks in this market are frequently wrong by minutes or days — a phone
that has never seen the internet may boot to 1970. **Anything keyed on client
time will silently skip or duplicate rows.**

#### Type mapping, Room → MySQL

| Kotlin / Room | MySQL | Note |
|---|---|---|
| `Long` (id) | `BIGINT` | |
| `String` (uuid) | `CHAR(36)` | Fixed width indexes far better than `VARCHAR` |
| `String` (name) | `VARCHAR(200)` | `utf8mb4` |
| `String` (notes) | `TEXT` | |
| `Double` (money) | `DECIMAL(12,2)` | **Never FLOAT** |
| `Double` (quantity) | `DECIMAL(12,3)` | 3 dp: goods sell by the 100 g |
| `Boolean` | `TINYINT(1)` | |
| `Long` (timestamp) | `BIGINT` | Epoch millis, UTC. Not `DATETIME` |

Store timestamps as epoch millis, not `DATETIME`. It sidesteps server timezone
configuration entirely, which on shared hosting you often cannot control.

---

## 4. The sync API

Six endpoints. Keep it boring.

```
POST /api/v1/pair          { code, device_name }        -> device_id, token
POST /api/v1/push          { changes: [...] }           -> per-row results
GET  /api/v1/pull?since=N&limit=500                     -> changes, next_cursor
GET  /api/v1/status                                     -> quota, devices, health
POST /api/v1/device/revoke { device_id }                -> owner only
GET  /api/v1/export                                     -> full dump for the shop
```

Auth: `Authorization: Bearer <device-token>` on everything except `/pair`.
Store only the SHA-256 of the token server-side.

### 4.1 Push

```
1. Read up to 200 outbox rows, oldest first, where nextAttemptAt <= now.
2. POST as one batch.
3. Server replies per row: ACCEPTED | DUPLICATE | CONFLICT | REJECTED.
4. ACCEPTED / DUPLICATE -> mark SYNCED, delete the outbox entry.
   CONFLICT             -> apply §4.3, re-queue if needed.
   REJECTED             -> record lastError, backoff, retry.
5. After 10 failures, surface it in the UI. Never fail silently.
```

Batch at 200 rows. A shop offline for a fortnight has thousands queued; one
giant request will time out on a weak connection, and shared hosts commonly cap
`max_execution_time` at 30 s and `post_max_size` at 8 MB.

**Idempotency is mandatory.** The client will retry after a timeout that
actually succeeded. `INSERT ... ON DUPLICATE KEY UPDATE` on `uuid` makes a
replayed batch harmless — return `DUPLICATE`, not an error.

### 4.2 Pull

```
GET /api/v1/pull?since=<server_seq>&limit=500
```

Returns rows with `server_seq > since`, ordered by `server_seq`, plus
`next_cursor`. Apply each batch in one Room transaction. Repeat until the
server says there is no more.

### 4.3 Conflict resolution, per entity

Most POS data is append-only, so conflicts are rarer than they look. Be
explicit anyway:

| Entity | Rule | Why |
|---|---|---|
| `sales`, `sale_items` | **Append only.** Same UUID = same row | A completed sale is history |
| `stock_movements` | **Append only.** Stock is a *derived sum* | Two devices selling the same item must both decrement |
| `products` | **Field-level merge**, last-write-wins per field | Owner edits price while staff edits name — both survive |
| `customers` | Field merge; `creditBalance` **derived** | Same reason as stock |
| `credit_transactions` | Append only | It is a ledger |
| `expenses`, `purchases` | Append only; edits field-merge | |
| `staff`, permissions | **Server wins.** Owner device only | Stops a compromised till granting itself access |
| `business_profile` | Server wins, owner device may write | |
| `notification_settings` | **Per device. Do not sync** | Owner's phone and the till want different alerts |
| `held_sales` | **Per device. Do not sync** | A parked bill belongs to its counter |
| `cash_register_shifts`, `cash_movements` | Per device; sync read-only to owner | A drawer belongs to one physical counter |
| `audit_log` | Append only, never deleted | |

**Never sync a computed balance.** `product.currentStock` and
`customer.creditBalance` must become derived (`SUM` over their movement tables)
or you will lose transactions. This is the single most common way POS sync
systems silently corrupt data. If summing on every read is too slow, cache the
total locally and recompute after each sync — but the movements stay the truth.

### 4.4 Ordering

Sync in dependency order or foreign keys will reject the batch:

```
shops → devices → business_profile → staff → products → customers → suppliers
  → sales → sale_items
  → credit_transactions → stock_movements → purchases → purchase_items
  → expenses → cash_shifts → cash_movements → audit_log
```

### 4.5 Optional features and sync

Every optional feature must degrade cleanly. A shop with the cash drawer off
has no shifts; a shop with stock off has no movements. **The server must not
assume any table is non-empty**, and pull must not fail because a shop has zero
`cash_register_shifts`. Feature flags live on `business_profile` and sync with
it, so a second device inherits the same simplified app.

---

## 5. Scheduling

**WorkManager**, not a foreground service and not a raw coroutine loop.

```kotlin
PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()
```

Plus expedited one-off syncs on: sale completed, day closed, app backgrounded,
manual "Sync now". Fifteen minutes is the Android floor for periodic work — do
not fight it.

**Do not sync on every cart change.** 300 sales/day should mean ~300 pushes,
not 30,000. On shared hosting that difference is the line between fine and
suspended for resource abuse.

---

## 6. Limits, quotas and caching

### 6.1 Quotas

A tier that silently stops accepting writes is worse than no cloud at all.

| Limit | Free | Paid | At the ceiling |
|---|---|---|---|
| Devices per shop | 3 | 10 | Block at pairing, name the devices using slots |
| Rows/month | 50,000 | 500,000 | Keep working locally, queue, warn |
| Storage | 100 MB | 1 GB | Stop syncing images first, then old data |
| Sync interval | 15 min | 5 min | Just longer |
| Cloud retention | 12 months | 5 years | Older stays on device, archived server-side |

**Exceeding a limit degrades sync, never selling.** Banner, never a modal.

### 6.2 MySQL tuning

Shared hosting gives you 25–50 connections total, across every site on the box.

- **No connection pool from the app.** Each request opens and closes one PDO
  connection. Persistent connections on shared hosting exhaust the limit.
- **`innodb_buffer_pool_size`** ≈ 50–70% of RAM on a VPS. Untouchable on shared.
- **Batch inserts** — one multi-row `INSERT`, not 200 round trips.
- **Index every `(shop_id, server_seq)`** pair. Pull is the hot query.
- Watch `max_allowed_packet` (often 4–16 MB). Batch size 200 keeps you clear.
- Add `LIMIT` to every query without exception.

### 6.3 Local database limits

SQLite handles this scale easily; a phone running five years does not.

- **Trim `audit_log` and `notifications`** to the newest 500–2000 rows.
  (`trimNotifications` already does this; do the same for the audit log.)
- **Archive sales older than 12 months** once confirmed synced.
- **`VACUUM` monthly** via WorkManager — SQLite does not reclaim space on delete.
- **Cap `sync_outbox`.** Past ~10,000 rows the device has been offline far too
  long; warn loudly rather than silently accumulating.
- `sale_items` grows fastest: roughly `sales × basket size`.

### 6.4 Caching

| Layer | What | Invalidation |
|---|---|---|
| Room | Everything. The permanent cache | Never; it is the truth |
| In-memory `StateFlow` | Products, customers, profile, permissions | Room `Flow` emits |
| Computed | Today's totals, low-stock counts | Recompute on the source flow |
| Images | Product photos on disk via Coil | LRU, ~50 MB cap |
| Server | `ETag`/`If-None-Match` on pull | 304 when nothing changed |

The app already caches correctly via Room `Flow` → `StateFlow`. **Do not add a
network cache layer.** The offline database *is* the cache; a second one only
creates a second thing to be stale.

### 6.5 Payload economy

Shops pay for mobile data by the megabyte.

- `gzip` request and response bodies (`ob_gzhandler` or nginx `gzip on`).
- Send **changed fields only** on update.
- Images on Wi-Fi only by default.
- Target: **a day's trading under 500 KB.**

---

## 7. Cloud availability as an optional feature

Default **off**. The switch lives in Settings.

| State | UI | Selling |
|---|---|---|
| `DISABLED` | No sync UI at all | Normal |
| `PAIRING` | Code entry | Normal |
| `SYNCED` | "All saved · 2 min ago" | Normal |
| `PENDING` | "12 sales waiting to save" | Normal |
| `OFFLINE` | "No internet — saving on this phone" | Normal |
| `ERROR` | "Could not save to cloud. Tap for help" | Normal |
| `QUOTA` | "Cloud storage full" + upgrade path | Normal |

Note the last column. **If a state you are adding would block a sale, the
design is wrong.**

### 7.1 Pairing without accounts

The shop has no email address and no password habits.

```
Owner device:  Settings → Cloud backup → Turn on
               → creates the shop, shows a 6-digit code valid 10 minutes
New device:    Settings → Cloud backup → Join a shop → enter code
               → server issues a long-lived device token
Owner device:  "Stock room phone wants to join" → Approve / Deny
```

Store the token in `EncryptedSharedPreferences`, never in Room. The owner must
be able to revoke a device remotely — phones get lost and staff leave.

### 7.2 Language

Plain, matching the rest of the app. Never "sync", "server", "conflict", "API".

| Instead of | Say |
|---|---|
| "Sync failed" | "Could not save to the cloud. Your sales are safe on this phone." |
| "Conflict detected" | "This item was changed on another phone. Keeping the newest." |
| "Offline mode" | "Working without internet. Everything is being saved here." |
| "Quota exceeded" | "Cloud storage is full. Selling still works normally." |

---

## 8. Security

Self-hosting means the security is *yours*. None of this is optional.

- **HTTPS only.** Let's Encrypt is free; most cPanel hosts have AutoSSL. Reject
  plain HTTP at the server, and certificate-pin in the app.
- **Every query filtered by `shop_id`**, taken from the authenticated token —
  **never** from a request parameter. This is the whole tenancy boundary. One
  missing `WHERE shop_id = ?` leaks one shop's takings to another.
- **Prepared statements everywhere** (PDO with `emulate_prepares = false`).
  Never concatenate SQL.
- **Never sync PINs.** Not even hashed. PINs are device-local.
- **Hash device tokens** (SHA-256) at rest. A database dump must not yield
  working credentials.
- **Rate-limit per device.** A client stuck in a retry loop must not be able to
  take the server down. 60 requests/minute is generous.
- **Automated MySQL backups**, `mysqldump` nightly to off-server storage, with
  a **tested restore**. An untested backup is not a backup.
- **A dedicated MySQL user** with only `SELECT, INSERT, UPDATE, DELETE` on the
  one database. Never `root`, never `GRANT ALL`.
- Keep the credentials outside the web root; on shared hosting that means above
  `public_html`, never in a file the server might serve as text.
- Provide a real **export** and a real **delete my data** path. The data belongs
  to the shop.

---

## 9. Notifications across devices

Notifications are **already built and working locally** (see
`data/model/Notifications.kt`). This section covers extending them across
devices.

### 9.1 What exists today

- 13 alert types, each permission-gated, with an importance and a default.
- Master switch, per-type switches, large-sale and large-discount thresholds,
  quiet hours handling a window crossing midnight.
- Alerts are **recorded** even when quiet hours suppress the buzz.
- Only alerts the signed-in person may see are shown; types for features the
  shop has switched off are hidden entirely.
- Stored in `notifications`, trimmed to the newest 500.

### 9.2 What cross-device adds

The owner is not at the shop. That is the entire point.

```
Counter device: sale completes
  → writes NotificationEntity locally  (works offline, unchanged)
  → enqueues a sync push
Server: receives the sale
  → evaluates the OWNER's notification settings, not the till's
  → sends a push to the owner's registered devices
Owner phone: system notification, even with the app closed
```

**Delivery without Firebase is the one genuinely hard part of self-hosting.**
Android has no built-in push channel; FCM *is* the mechanism, and Google
provides it free with no server dependency beyond an HTTP call. Options:

| Option | Reality |
|---|---|
| **FCM (HTTP v1)** | Free, reliable, works when the app is closed. Your server calls Google's endpoint; shop data stays on your server — only a title and body transit. **Recommended.** |
| **WebSockets / long poll** | No third party, but a socket cannot survive Doze. The owner will miss overnight alerts. Fine as a *supplement* while the app is open |
| **Periodic pull** | WorkManager every 15 min, notify locally. No third party at all, but up to 15 minutes late and it costs battery |
| **SMS gateway** | Costs money per message; genuinely useful for `CASH_SHORTAGE` only |

**Recommendation: FCM for delivery, with a 15-minute WorkManager pull as the
fallback** for owners who want zero Google involvement. Using FCM for the
*notification envelope* does not compromise self-hosting: put no shop figures in
the payload, just "open the app", and let the app fetch details from your server.

Also required:

1. **Server-side evaluation.** The owner's thresholds and quiet hours live with
   the owner's device record. A till must not decide what the owner hears.
2. **Deduplicate.** If the owner is at the counter, one notification, not two.
   Suppress the push when the same device generated it.
3. **Android 13+ needs `POST_NOTIFICATIONS`.** Ask when the owner first enables
   an alert, not at first launch.
4. **Notification channels** per importance — `HIGH`, `NORMAL`, `QUIET` — so
   the owner can tune loudness in Android settings, where they will look.
5. **Batch the noisy ones.** "23 sales today, Rs. 45,600" beats 23 buzzes.

### 9.3 Additional cross-device alert types

```kotlin
DEVICE_JOINED       // "Stock room phone joined your shop"
DEVICE_OFFLINE      // "Counter phone has not synced for 3 hours"
SYNC_FAILING        // "Sales are not reaching the cloud"
DAILY_SUMMARY       // scheduled digest, 8pm
UNUSUAL_ACTIVITY    // sales outside normal hours, large voids
```

`DEVICE_OFFLINE` matters more than it looks: it is how an owner learns the shop
phone died *before* they lose a day of records.

---

## 10. Build order

Each phase must ship working.

| Phase | Deliverable | Done when |
|---|---|---|
| ~~0~~ | ~~Real Room migrations~~ | **Done.** `data/db/Migrations.kt`, v1→v7, no destructive fallback |
| ~~0b~~ | ~~Derive stock and credit from their ledgers~~ | **Done.** See Appendix C |
| 1 | UUIDs, sync columns, outbox; writes populate the outbox | Outbox fills; app behaves identically |
| 2 | MySQL schema, PHP API skeleton, HTTPS, `/status` | `curl` returns healthy |
| 3 | Pairing, tokens, revocation | Two devices bound to one shop |
| 4 | Push only. One device up, others read-only | Sales appear in MySQL |
| 5 | Pull + merge. Derived stock and credit balances | Two tills sell one item; stock is right |
| 6 | Conflict rules per §4.3, plus a conflict log the owner can read | Deliberate conflicts resolve as documented |
| 7 | Quotas, retention, archival, backups with a tested restore | Ceilings degrade gracefully |
| 8 | FCM + server-side evaluation, channels, digests | Owner notified with the app closed |
| 9 | Export, delete-my-data, remote revocation | |

**Phases 0 and 0b are complete** — they were the two prerequisites that would
have made everything after them unsafe. Sync work can now start at Phase 1.

---

## 11. Testing

The failures that matter only appear in bad conditions.

- **Two devices, both offline, both sell the last unit.** Stock must not go to
  `-1` silently; the merge must flag the oversell.
- **Clock skew.** One device at 1970, one at 2030. No rows lost.
- **Kill mid-sync.** Force-stop during a push. No duplicates, no loss.
- **Offline two weeks**, then reconnect. Batching must not time out.
- **Airplane mode, 100 sales.** Everything queues, nothing blocks.
- **Quota exceeded mid-day.** Selling continues.
- **Token revoked while offline.** Graceful re-pair, no data loss.
- **Duplicate UUID** from a restored backup on a new phone. Clean rejection.
- **MySQL down.** The app must show "could not save" and keep selling.
- **Shared host kills a request at 30 s.** Partial batch must not corrupt state.
- **Restore from `mysqldump` into an empty database.** Do this before launch,
  not after an incident.

Instrument in production: outbox depth, sync duration, conflict rate, failure
rate by error type. A rising conflict rate means a merge rule is wrong.

---

## 12. Decisions to make before writing code

1. **Shared hosting or VPS?** (Shared + PHP to start is fine and cheapest.)
2. **Server language?** PHP 8.2 runs everywhere; Node/Kotlin need a VPS.
3. **UUID migration**: full switch, or UUID alongside the existing `Long` keys?
4. **FCM, or pull-only?** Affects how quickly an owner learns about a refund.
5. **Pricing**: is cloud free or paid? This sets the quota numbers.
6. **Region**: Singapore or Mumbai for latency to Sri Lanka. Avoid US/EU hosts.
7. **Multi-branch owners**: does one owner run several shops? Changes tenancy
   significantly — decide now, not later.

---

## 13. Prompt to hand to an implementer

> You are extending Arro-POS, an offline-first Android POS built with Kotlin,
> Jetpack Compose and Room, for small Sri Lankan shops.
>
> Implement cross-device sync per `docs/CLOUD_SYNC_MASTER_PROMPT.md`, starting
> at Phase 1. Phases 0 and 0b are already done: real Room migrations are in
> `data/db/Migrations.kt` with schemas exported to `app/schemas`, and stock and
> credit are already derived from `stock_movements` and `credit_transactions`.
>
> Hosting is **self-hosted: our own VPS or shared hosting, our own MySQL 8**.
> No Firebase, Supabase or any third-party backend-as-a-service for data. FCM
> may be used for the notification envelope only, carrying no shop figures.
>
> Absolute constraints:
> - Room stays the source of truth. No UI code path may await the network.
> - A sale must complete in under two seconds with networking disabled.
> - Sync is optional and defaults to off; with it off the app is fully working.
> - Every money column is `DECIMAL(12,2)` in MySQL. Never `FLOAT`.
> - Incremental pull uses a server-issued sequence, never a device clock.
> - Every query is filtered by `shop_id` from the authenticated token, never
>   from a request parameter.
> - Never sync a computed balance. Stock and credit already derive from their
>   movement tables - keep it that way, and make the sync merge rule "append".
> - Never reintroduce `fallbackToDestructiveMigration`. Never sync staff PINs.
> - Optional features (stock, credit, cash drawer, staff) may each be off. No
>   table may be assumed non-empty.
> - All user-facing wording is plain language: no "sync", "server", "conflict"
>   or "API". The reader owns a grocery shop, not a laptop.
>
> Work in vertical slices, each independently shippable. After each phase, state
> plainly what is tested and what is not.

---

## Appendix A: current schema

Entities as of Room v7, in `data/model/Entities.kt` and
`data/model/Notifications.kt`:

```
business_profile      1 row: shop settings, printer, receipt design, feature flags
products              catalogue, stamped with shopType for category isolation
sales                 completed bills (immutable)
sale_items            bill lines, with unitPrice as actually sold
customers             credit book; creditBalance MUST become derived
credit_transactions   the credit ledger (append only)
suppliers             who you buy from
purchases             supplier bills
purchase_items        supplier bill lines
stock_movements       every stock change (append only) — the truth for stock
expenses              rent, electricity, transport
staff                 name, role, PIN, per-person permission overrides
cash_register_shifts  day open / close (only if cashDrawerEnabled)
cash_movements        cash in / out during a shift
held_sales            parked bills (device-local, do not sync)
audit_log             who did what (append only)
notifications         alert history
notification_settings per-device alert preferences (do not sync)
```

## Appendix B: optional features

Each is independently switchable and affects what syncs:

| Flag on `business_profile` | Off means | Sync impact |
|---|---|---|
| `trackStock` | No stock numbers anywhere | `stock_movements` stays empty |
| `creditEnabled` | Everyone pays now | `credit_transactions` stays empty |
| `cashDrawerEnabled` | No open/close routine | `cash_register_shifts` stays empty |
| `staffEnabled` | Solo owner, no PIN | `staff` has 0–1 rows |

**The server must treat every one of these tables as legitimately empty.**

## Appendix C: the two prerequisites (both now done)

These were the blockers. Both are fixed; this records what was done so the
reasoning is not lost.

### 1. Real migrations — `data/db/Migrations.kt`

`fallbackToDestructiveMigration` is gone. There are now six migrations
covering v1→v7, registered via `ALL_MIGRATIONS`, and `exportSchema = true`
writes every version's schema into `app/schemas` (committed) so Room can
validate migrations at build time.

Deliberately **no destructive fallback remains**. A missing migration now
crashes loudly in testing instead of silently wiping a shop.

Unit tests pin the chain: every version has a migration, the chain is unbroken
with no duplicates, and it ends on `CURRENT_DB_VERSION`. `/tmp/audit.py` fails
if `fallbackToDestructiveMigration` is ever reintroduced.

One subtlety worth remembering: `MIGRATION_4_5` defaults `cashDrawerEnabled` to
**1**, while the Kotlin entity defaults it to **false**. That difference is
intentional — shops already using the drawer screens before it became optional
keep it, while new shops start without it.

### 2. Stock and credit are derived

`stock_movements` and `credit_transactions` are now the truth.
`products.currentStock` and `customers.creditBalance` are caches over them.

- Every writer **appends a movement, then recomputes** the cached total.
- The increment-style queries (`adjustProductStock`,
  `updateCustomerCredit`) have been **deleted**, not just avoided — that
  operation is precisely what loses a sale when two devices do it at once.
- Opening stock enters the ledger as an `INITIAL` movement, both when the
  catalogue is seeded and when a product is added by hand, so the sum is never
  wrong for a product that has simply never moved.
- A recount records the *difference* between the ledger and the counted figure,
  so it states the truth rather than adding to it.
- `refreshAllStock()` and `refreshAllCredit()` run once at launch, so a cache
  that drifted — app killed mid-write, or a future sync merging movements from
  another device — quietly repairs itself.

The sign convention lives in exactly one place, the DAO:

```sql
-- credit
SUM(CASE WHEN type = 'PAYMENT' THEN -amount ELSE amount END)
-- stock
SUM(changeQty)
```

**For sync this means the merge rule is simply "append".** Two devices that
each sell the last unit produce two movements, and the total is right. That is
the property the whole multi-device design rests on.
