# KadePOS — Cross-Device Sync, Cloud Availability & Notifications

**Status: planning document. Nothing described here is built yet.**
This is the brief for a future phase. The app today is single-device and
offline-only, and that is deliberate — read "Why offline-first is not
negotiable" before changing anything.

Written for: whoever implements multi-device sync, whether that is a person or
an AI agent. It is meant to be handed over whole as a master prompt.

---

## 0. Context you must not lose

KadePOS is an Android point-of-sale app for small Sri Lankan shops — groceries,
pharmacies, salons, hardware stores, repair counters. It is built around one
sentence: **Open → Sell → Pay → Print.**

Facts about the current build that constrain every decision below:

| Thing | Current state |
|---|---|
| Storage | Room / SQLite on the device. `kadepos_database`, currently **v4** |
| Network | **None.** The app makes zero network calls today |
| Printing | Real Bluetooth SPP + Wi-Fi TCP (port 9100) ESC/POS |
| Identity | Per-staff 4-digit PIN, no accounts, no email, no server |
| Roles | 19 permissions, 4 roles, per-person overrides stored as CSV on the staff row |
| Money | LKR only, `Rs.` prefix |
| Migrations | `fallbackToDestructiveMigration(dropAllTables = true)` — **must be fixed before any shop is live** |
| Entities | 18, including `sales`, `sale_items`, `products`, `customers`, `credit_transactions`, `suppliers`, `purchases`, `stock_movements`, `expenses`, `staff`, `cash_register_shifts`, `cash_movements`, `held_sales`, `audit_log`, `notifications`, `notification_settings` |

### Why offline-first is not negotiable

The typical shop has one Android phone, patchy mobile data, and daily power
cuts. A sale must complete in under two seconds with the aeroplane mode on. If
the cloud is ever on the critical path of taking money from a customer, the
product has failed. Every rule below follows from this.

**The test to apply to any design:** turn off all networking, sell twenty items,
take cash, print. If anything blocks, spins, or errors, the design is wrong.

---

## 1. Target architecture

### 1.1 Shape

```
   Device A (counter)        Device B (stock room)     Device C (owner phone)
   ┌────────────────┐        ┌────────────────┐        ┌────────────────┐
   │  Compose UI    │        │  Compose UI    │        │  Compose UI    │
   │  PosViewModel  │        │       ...      │        │       ...      │
   │  PosRepository │        │                │        │                │
   │  Room (truth)  │◄─local─┤                │        │                │
   │  Outbox table  │        │                │        │                │
   └───────┬────────┘        └───────┬────────┘        └───────┬────────┘
           │  SyncWorker (WorkManager, opportunistic)          │
           └──────────────────┬───────────────────────────────┘
                              ▼
                   ┌──────────────────────┐
                   │   Sync API (HTTPS)   │
                   │  per-shop tenancy    │
                   ├──────────────────────┤
                   │  Postgres + storage  │
                   └──────────────────────┘
```

**Room stays the source of truth on every device.** The cloud is a
synchronisation and backup medium, never a read dependency. The UI must never
`await` the network.

### 1.2 The non-negotiable rules

1. **No UI code path may await a network call.** Ever.
2. **Writes go to Room first**, then to an outbox. Sync drains the outbox later.
3. **A sale is immutable once completed.** It syncs as an append. This removes
   most conflicts before they can exist.
4. **Sync is per shop**, not per device. A shop is the tenant boundary.
5. **Turning sync off must leave a fully working app.** It is a feature, not a
   foundation.

---

## 2. Choosing the backend

Three realistic options. Pick one and commit; do not abstract over all three.

| | Supabase | Firebase | Custom (Ktor + Postgres) |
|---|---|---|---|
| Offline SDK | No real one for Android | Firestore has one | You build it |
| Data model | Postgres, relational — matches Room | Document store, awkward for sale/sale_items | Postgres |
| Row-level security | Excellent, SQL policies | Rules DSL, gets hairy | You build it |
| Cost at 500 shops | Low, predictable | Reads are metered; a sync loop bug is expensive | VPS cost only |
| Sri Lanka latency | ap-south-1 ≈ 40–80 ms | ap-south1 similar | Wherever you host |
| Ops burden | Low | Lowest | Highest |

**Recommendation: Supabase.** The relational model maps directly onto the
existing Room schema, row-level security gives per-shop isolation almost for
free, and its lack of an offline SDK does not matter — you are writing your own
sync layer anyway, because the offline behaviour is the product.

**Avoid Firestore** specifically because per-read billing punishes exactly the
pattern a POS produces (frequent small syncs from many devices), and because
`sales` → `sale_items` is a relational shape you would fight constantly.

---

## 3. Data model changes

### 3.1 Every syncable table gains

```kotlin
val serverId: String? = null,        // UUID from the server, null until first sync
val shopId: String = "",             // tenant key
val updatedAt: Long = 0L,            // device clock, last local edit
val serverUpdatedAt: Long = 0L,      // server clock, last confirmed sync
val syncState: String = "PENDING",   // PENDING, SYNCED, CONFLICT, FAILED
val deletedAt: Long? = null,         // soft delete; never hard-delete a synced row
val originDevice: String = ""        // which device created it
```

**Use UUIDs, not autoincrement Longs, for anything that syncs.** Two devices
offline will both mint `id = 47`. Generate a UUID at creation on the device;
the server accepts it as the primary key. This single decision removes an
entire class of merge bug.

Keep the existing `Long` primary keys for Room's internal relations if a full
migration is too invasive, but add the UUID as a unique indexed column and make
it the sync identity.

### 3.2 The outbox

```kotlin
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityTable: String,
    val entityUuid: String,
    val operation: String,        // INSERT, UPDATE, DELETE
    val payloadJson: String,      // the full row, so replay needs no lookup
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String = "",
    val nextAttemptAt: Long = 0L  // for backoff
)
```

Write to `sync_outbox` in the **same Room transaction** as the business write.
If the app is killed between the two, you get a silent divergence that nobody
notices for weeks.

### 3.3 Sync metadata

```kotlin
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val shopId: String = "",
    val deviceId: String = "",          // stable UUID, generated once on install
    val deviceName: String = "",        // "Front counter", set by the owner
    val cloudEnabled: Boolean = false,  // the master switch
    val lastPullAt: Long = 0L,
    val lastPushAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val pullCursor: String = "",        // server watermark for incremental pull
    val pendingCount: Int = 0,
    val lastError: String = ""
)
```

---

## 4. The sync algorithm

### 4.1 Push (outbox drain)

```
1. Read up to 200 outbox rows, oldest first, where nextAttemptAt <= now.
2. Group by table; POST as one batch per table.
3. Server replies per-row: ACCEPTED | CONFLICT | REJECTED.
4. ACCEPTED  -> mark row SYNCED, set serverUpdatedAt, delete outbox entry.
   CONFLICT  -> apply the resolution rules (§4.3), re-queue if needed.
   REJECTED  -> record lastError, increment attempts, exponential backoff.
5. After 10 failed attempts, surface it in the UI. Never fail silently.
```

Batch size of 200 keeps a request under a few hundred KB, which survives a
2G-ish connection. Do not push the whole outbox in one request; a shop that has
been offline for a fortnight will have thousands of rows.

### 4.2 Pull (incremental)

```
GET /sync/changes?shop_id=…&since=<pullCursor>&limit=500
```

The server returns rows with `server_updated_at > since`, ordered, plus a new
cursor. Apply inside one Room transaction per batch. Repeat until the server
reports no more.

**Use a server-issued cursor, not a client timestamp.** Device clocks in this
market are frequently wrong by minutes to days — a phone that has never seen
the internet may boot to 1970. Anything keyed on client time will silently skip
or duplicate rows.

### 4.3 Conflict resolution, per entity

Conflicts are rarer than they look, because most POS data is append-only. Be
explicit anyway:

| Entity | Rule | Why |
|---|---|---|
| `sales`, `sale_items` | **Append only, never conflicts.** UUID collision = same row | A completed sale is history. It cannot be edited |
| `stock_movements` | **Append only.** Stock level is a *derived sum*, not a stored value | Two devices selling the same item must both decrement. Last-write-wins would lose a sale |
| `products` | **Field-level merge**, last-write-wins per field by `updatedAt` | Owner edits the price on their phone while staff edits the name on the counter — both should survive |
| `customers` | Field-level merge; `creditBalance` is **derived** from `credit_transactions` | Same reason as stock |
| `credit_transactions` | Append only | It is a ledger |
| `expenses`, `purchases` | Append only; edits are field-merge | |
| `staff`, permissions | **Server wins.** Only an owner device may write | Prevents a compromised counter phone granting itself access |
| `business_profile` | Server wins, owner device may write | |
| `notification_settings` | **Per device**, do not sync | The owner's phone and the till want different alerts |
| `held_sales` | **Per device**, do not sync | A parked bill belongs to the counter it was parked at |
| `audit_log` | Append only, never deleted | |

**The critical insight: never sync a computed balance.** `product.currentStock`
and `customer.creditBalance` must become derived values (`SUM` over their
movement tables) or you will lose transactions. This is the single most common
way POS sync systems silently corrupt data. If deriving on every read is too
slow, cache the sum locally and recompute after each sync — but the movements
remain the truth.

### 4.4 Ordering

Sync in dependency order or you get foreign-key failures:

```
business_profile → staff → products → customers → suppliers
  → sales → sale_items
  → credit_transactions → stock_movements → purchases → purchase_items
  → expenses → cash_shifts → cash_movements → audit_log
```

---

## 5. Scheduling and battery

Use **WorkManager**, not a foreground service and not a raw coroutine loop.

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

Plus **expedited one-off syncs** on: sale completed, day closed, app
backgrounded, and manual "Sync now". Fifteen minutes is the Android floor for
periodic work — do not fight it.

**Do not sync on every keystroke or every cart change.** A shop doing 300 sales
a day should produce roughly 300 pushes, not 30,000.

---

## 6. Limits, quotas and caching

### 6.1 Enforce ceilings before the shop hits them

A free tier that silently stops accepting writes is worse than no cloud at all.
Define limits, show them, and degrade gracefully:

| Limit | Suggested | Behaviour at the ceiling |
|---|---|---|
| Devices per shop | 3 free / 10 paid | Block the 4th at pairing, name the devices already using slots |
| Rows synced per month | 50,000 free | Keep working locally; queue and warn |
| Storage per shop | 100 MB free | Stop syncing images first, then old data |
| Sync frequency | 15 min free / 5 min paid | Just a longer interval |
| Retention in cloud | 12 months free | Older data stays on device; archive server-side |
| Attachments | Paid only | |

**Rule: exceeding a limit degrades sync, never selling.** If the quota is blown,
the till keeps working offline and shows a banner. Never a modal, never a block.

### 6.2 Local database limits

SQLite handles this scale easily, but a phone that runs for five years does not:

- **Trim `audit_log` and `notifications`** to the newest ~500–2000 rows.
  (`trimNotifications` already does this; do the same for the audit log.)
- **Archive sales older than 12 months** to a compressed table or a JSON export,
  once confirmed synced.
- **Vacuum monthly** via WorkManager. SQLite does not reclaim space on delete.
- **Cap `sync_outbox`.** If it exceeds ~10,000 rows the device has been offline
  far too long — warn the owner loudly rather than quietly accumulating.
- Watch the row counts that actually grow: `sale_items` grows fastest, roughly
  `sales × average basket size`.

### 6.3 Caching

| Layer | What | Invalidation |
|---|---|---|
| Room | Everything. The permanent cache | Never; it is the truth |
| In-memory `StateFlow` | Products, customers, profile, permissions | Room `Flow` emits automatically |
| Computed | Today's totals, low-stock counts | Recompute on the source flow |
| Images | Product photos, on disk with Coil | LRU, capped at ~50 MB |

The app already caches correctly via Room `Flow` → `StateFlow`. **Do not add a
network cache layer.** The offline database *is* the cache; a second one only
creates a second thing to be stale.

### 6.4 Payload economy

Shops pay for mobile data by the megabyte.

- `gzip` every request and response.
- Send **changed fields only** on update, not whole rows.
- Never sync images on mobile data by default — Wi-Fi only, with an override.
- Target: **a day's trading under 500 KB** for a typical shop.

---

## 7. Cloud availability as an optional feature

The switch lives in Settings and defaults to **off**.

### 7.1 States to design for

| State | UI | Selling |
|---|---|---|
| `DISABLED` | No sync UI at all | Normal |
| `PAIRING` | Code entry | Normal |
| `SYNCED` | "All saved · 2 min ago" | Normal |
| `PENDING` | "12 sales waiting to save" | Normal |
| `OFFLINE` | "No internet — saving on this phone" | Normal |
| `ERROR` | "Could not save to cloud. Tap for help" | Normal |
| `QUOTA` | "Cloud storage full" + upgrade path | Normal |

Note the last column. **Selling is never affected by any sync state.** If a
state you are adding would block a sale, you have designed it wrong.

### 7.2 Pairing without accounts

The shop has no email address and no password habits. Do not force an account.

```
Owner device:  Settings → Cloud backup → Turn on
               → creates the shop, shows a 6-digit code valid for 10 minutes
New device:    Settings → Cloud backup → Join a shop → enter code
               → server binds device to shop, issues a long-lived device token
Owner device:  sees "Stock room phone wants to join" → Approve / Deny
```

Store the device token in `EncryptedSharedPreferences`, never in Room. The
owner must be able to revoke a device remotely — phones get lost and staff
leave.

### 7.3 Language

Plain, in the same register as the rest of the app. Never "sync", "server",
"conflict" or "API".

| Instead of | Say |
|---|---|
| "Sync failed" | "Could not save to the cloud. Your sales are safe on this phone." |
| "Conflict detected" | "This item was changed on another phone. Keeping the newest." |
| "Offline mode" | "Working without internet. Everything is being saved here." |
| "Quota exceeded" | "Cloud storage is full. Selling still works normally." |

---

## 8. Security

- **HTTPS only**, certificate pinning on the sync host.
- **Row-level security keyed on `shop_id`.** A device token must be
  structurally incapable of reading another shop's rows. Enforce in the
  database, not in application code.
- **Never sync PINs.** Not even hashed. PINs are device-local. If staff need to
  sign in on a second device, the owner sets a PIN there.
- **Encrypt at rest** on the server; consider SQLCipher on the device for shops
  that want it.
- **Audit every sync**: which device pushed what, when. Extend the existing
  `audit_log`.
- **Rate-limit per device** server-side. A buggy client in a retry loop must
  not be able to take the backend down.
- Data belongs to the shop: provide a full **export** (CSV/JSON) and a real
  **delete my data** path.

---

## 9. Notifications across devices

Notifications are **already built and working locally** (see
`data/model/Notifications.kt`). This section covers extending them across
devices.

### 9.1 What exists today

- 13 alert types, each with a permission gate, an importance, and a default.
- A master switch, per-type switches, large-sale and large-discount thresholds,
  and quiet hours that correctly handle a window crossing midnight.
- Alerts are **recorded** even when quiet hours suppress the buzz.
- Only alerts the signed-in person is entitled to see are shown.
- Stored in the `notifications` table, trimmed to the newest 500.

### 9.2 What cross-device adds

The owner is not at the shop. That is the entire point.

```
Counter device: sale completes
  → writes NotificationEntity locally  (works offline, unchanged)
  → enqueues a sync push
Server: receives the sale
  → evaluates the OWNER's notification settings, not the till's
  → sends FCM to the owner's registered devices
Owner phone: system notification, even though the app is closed
```

Requirements:

1. **Firebase Cloud Messaging** for delivery. Register a device token per
   device, tied to the staff member signed in there.
2. **Server-side evaluation.** The owner's thresholds and quiet hours live with
   the owner's device record, not the till's. A till must not decide what the
   owner hears.
3. **Deduplicate.** If the owner is physically at the counter, they should get
   one notification, not one local and one push. Suppress the push when the
   same `shop_id` + `device_id` generated it.
4. **Android 13+ requires `POST_NOTIFICATIONS`.** Ask for it at a moment that
   makes sense — when the owner first switches an alert on, not at first launch.
5. **Notification channels**, one per importance: `HIGH` (refunds, cash
   shortages, blocked attempts), `NORMAL`, `QUIET`. This lets the owner tune
   loudness in Android settings, which is where they will look.
6. **Batch the noisy ones.** "23 sales today, Rs. 45,600" beats 23 buzzes.
   Digest anything `QUIET`; deliver `HIGH` immediately.

### 9.3 Additional cross-device alert types

```kotlin
DEVICE_JOINED       // "Stock room phone joined your shop"
DEVICE_OFFLINE      // "Counter phone has not synced for 3 hours"
SYNC_FAILING        // "Sales are not reaching the cloud"
DAILY_SUMMARY       // scheduled digest, 8pm
UNUSUAL_ACTIVITY    // sales outside normal hours, big voids
```

`DEVICE_OFFLINE` matters more than it looks: it is how an owner finds out the
shop phone died before they lose a day of records.

---

## 10. Build order

Do not attempt this in one pass. Each phase must ship working.

| Phase | Deliverable | Done when |
|---|---|---|
| **0** | **Real Room migrations.** Remove `fallbackToDestructiveMigration` | An upgrade preserves data. **Blocks everything else** |
| 1 | UUIDs, sync columns, outbox table, writes populate the outbox | Outbox fills correctly; app behaves identically |
| 2 | Backend: schema, RLS, `/sync/push`, `/sync/changes` | Curl round-trips a sale |
| 3 | Device pairing, tokens, revocation | Two devices bound to one shop |
| 4 | Push only. One device up, others read-only | Sales appear in the cloud |
| 5 | Pull + merge. Derived balances for stock and credit | Two tills sell the same item; stock is correct |
| 6 | Conflict rules per §4.3, plus a conflict log the owner can read | Deliberate conflicts resolve as documented |
| 7 | Quotas, limits, degradation, retention | Ceilings behave gracefully |
| 8 | FCM, server-side notification evaluation, channels, digests | Owner is notified with the app closed |
| 9 | Export, delete-my-data, remote device revocation | |

**Phase 0 is a hard blocker.** The database currently drops every table on a
version bump. Shipping cloud sync on top of that would destroy real shops' data
on the first update.

---

## 11. Testing

The failures that matter are the ones that only appear in bad conditions.

- **Two devices, both offline, both sell the last unit.** Stock must not go to
  `-1` silently; the merge must produce a real number and flag the oversell.
- **Clock skew.** Set one device to 1970 and one to 2030. Sync must not lose
  rows.
- **Kill mid-sync.** Force-stop during a push. No duplicates, no lost rows.
- **Offline for two weeks**, then reconnect. Batching must not time out.
- **Airplane mode, 100 sales.** Everything queues, nothing blocks.
- **Quota exceeded mid-day.** Selling continues.
- **Token revoked** while the device is offline. Graceful re-pair, no data loss.
- **Duplicate UUID** from a cloned install (owners do restore backups to new
  phones). Server must reject cleanly.

Instrument in production: outbox depth, sync duration, conflict rate, failure
rate by error type. A rising conflict rate means a merge rule is wrong.

---

## 12. Decisions to make before writing code

1. **Backend**: Supabase, or self-host? (Recommendation: Supabase.)
2. **UUID migration**: full switch, or UUID alongside the existing `Long` keys?
3. **Pricing**: is cloud free, or the paid tier? This sets the quota numbers.
4. **Region**: `ap-south-1` (Mumbai) is closest to Sri Lanka.
5. **Multi-shop owners**: does one owner run several branches? Changes the
   tenancy model significantly — decide now, not later.
6. **Retention**: how long does the cloud keep data for a shop that stops paying?

---

## 13. Prompt to hand to an implementer

> You are extending KadePOS, an offline-first Android POS built with Kotlin,
> Jetpack Compose and Room, for small Sri Lankan shops.
>
> Implement cross-device sync per `docs/CLOUD_SYNC_MASTER_PROMPT.md`, starting
> at Phase 0 (real Room migrations — the database currently uses
> `fallbackToDestructiveMigration`, which would destroy live shop data).
>
> Absolute constraints:
> - Room stays the source of truth. No UI code path may await the network.
> - A sale must complete in under two seconds with networking disabled.
> - Sync is optional and defaults to off; with it off the app is fully working.
> - Never sync a computed balance. Stock and credit are derived from their
>   movement tables.
> - Never sync staff PINs.
> - All user-facing wording is plain language: no "sync", "server", "conflict"
>   or "API". The reader owns a grocery shop, not a laptop.
>
> Work in vertical slices, each independently shippable. After each phase,
> state plainly what is tested and what is not.

---

## Appendix: current schema reference

Entities as of DB v4, in `data/model/Entities.kt` and
`data/model/Notifications.kt`:

```
business_profile      1 row, shop settings, printer config, receipt design
products              catalogue, stamped with shopType for category isolation
sales                 completed bills (immutable)
sale_items            lines on a bill, with unitPrice as actually sold
customers             credit book; creditBalance MUST become derived
credit_transactions   the credit ledger (append only)
suppliers             who you buy from
purchases             supplier bills
purchase_items        lines on a supplier bill
stock_movements       every stock change (append only) — the truth for stock
expenses              rent, electricity, transport
staff                 name, role, PIN, per-person permission overrides
cash_register_shifts  day open / close
cash_movements        cash in / out during a shift
held_sales            parked bills (device-local, do not sync)
audit_log             who did what (append only)
notifications         alert history
notification_settings per-device alert preferences (do not sync)
```

**Note:** `products.currentStock` and `customers.creditBalance` are currently
stored values. Both must become derived from `stock_movements` and
`credit_transactions` respectively before multi-device sync. This is the single
most important schema change in this document.
