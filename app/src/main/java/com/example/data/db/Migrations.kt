package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations, one per schema version.
 *
 * Until now the database was built with `fallbackToDestructiveMigration`, which
 * silently drops every table whenever the version number changes. That is
 * survivable while the app is only on our own phones; it would destroy a real
 * shop's entire sales history, credit book and product list the first time they
 * installed an update. Nothing here is clever - it is just the difference
 * between an update and a disaster.
 *
 * Rules for adding one:
 *  - Never edit a migration that has shipped. Add the next one instead.
 *  - `ALTER TABLE ... ADD COLUMN` must supply a NOT NULL default that matches
 *    the Kotlin default exactly, or Room's schema validation will reject it.
 *  - Text columns are `TEXT NOT NULL DEFAULT ''`, booleans are
 *    `INTEGER NOT NULL DEFAULT 0/1`, money is `REAL NOT NULL DEFAULT 0.0`.
 *    Room maps Kotlin `Boolean` to INTEGER and `Double` to REAL; using any
 *    other affinity will fail validation even though SQLite itself allows it.
 */

/**
 * v1 -> v2. The rebrand from "The System" to KadePOS.
 *
 * Adds shop-type isolation, real printer hardware fields, the signed-in staff
 * record, and the audit log table. The v1 database also shipped with demo
 * defaults baked into the entity ("ABC Stores", a fake phone number, a fake
 * printer); those were only *defaults* for new rows, so no data rewriting is
 * needed here - the setup flow overwrites them.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- business_profile: shop type + real printer configuration ---
        db.execSQL("ALTER TABLE business_profile ADD COLUMN shopTypeKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN printerAddress TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN printerConnectionType TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN printerPort INTEGER NOT NULL DEFAULT 9100")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN activeStaffRole TEXT NOT NULL DEFAULT 'Owner'")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN requirePinOnOpen INTEGER NOT NULL DEFAULT 0")

        // --- products: which shop type owns this item ---
        db.execSQL("ALTER TABLE products ADD COLUMN shopType TEXT NOT NULL DEFAULT ''")

        // --- the audit log is new in v2 ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                staffId INTEGER NOT NULL DEFAULT 0,
                staffName TEXT NOT NULL DEFAULT '',
                action TEXT NOT NULL,
                description TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0.0,
                reference TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v2 -> v3. Role-based access control and the bill designer.
 *
 * Staff rows gain per-person permission overrides; the profile gains the
 * receipt-design switches.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- staff: per-person permission tweaks on top of the role ---
        db.execSQL("ALTER TABLE staff ADD COLUMN extraPermissions TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE staff ADD COLUMN revokedPermissions TEXT NOT NULL DEFAULT ''")

        // --- business_profile: what the printed bill shows ---
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptHeaderName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptHeaderNote TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptShowAddress INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptShowPhone INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptShowDateTime INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptShowCashier INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptShowItemCount INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE business_profile ADD COLUMN receiptReturnNote TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 -> v4. Notifications.
 *
 * Two new tables. `notification_settings` is seeded with a single row so the
 * app never has to special-case "no settings yet" on the hot path of
 * completing a sale.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0.0,
                actorName TEXT NOT NULL DEFAULT '',
                reference TEXT NOT NULL DEFAULT '',
                timestamp INTEGER NOT NULL,
                isRead INTEGER NOT NULL DEFAULT 0,
                importance TEXT NOT NULL DEFAULT 'NORMAL'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_settings (
                id INTEGER PRIMARY KEY NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                enabledKeys TEXT NOT NULL DEFAULT '',
                largeSaleThreshold REAL NOT NULL DEFAULT 10000.0,
                largeDiscountThreshold REAL NOT NULL DEFAULT 500.0,
                quietHoursEnabled INTEGER NOT NULL DEFAULT 0,
                quietFromHour INTEGER NOT NULL DEFAULT 21,
                quietToHour INTEGER NOT NULL DEFAULT 7
            )
            """.trimIndent()
        )
        // Seed the defaults an existing shop should wake up with. These keys
        // mirror the enum defaults; a unit test fails if the two ever drift.
        db.execSQL(
            """
            INSERT OR IGNORE INTO notification_settings
                (id, enabled, enabledKeys, largeSaleThreshold, largeDiscountThreshold,
                 quietHoursEnabled, quietFromHour, quietToHour)
            VALUES (1, 1, '$DEFAULT_NOTIFICATION_KEYS', 10000.0, 500.0, 0, 21, 7)
            """.trimIndent()
        )
    }
}

/**
 * v4 -> v5. The cash drawer becomes optional.
 *
 * Existing shops were already using the drawer screens before it was a choice,
 * so they migrate with it ON. Only brand new shops default to off (the entity
 * default), which is why this value deliberately differs from the Kotlin
 * default - it preserves what the shop already had.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE business_profile ADD COLUMN cashDrawerEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * The notification types switched on for a shop that has never chosen. Kept as
 * a literal because a migration must describe the schema *as it was at that
 * version* - it can never call into current app code, which will keep changing.
 * `NotificationDefaultsTest` fails if this drifts from the enum.
 */
const val DEFAULT_NOTIFICATION_KEYS: String =
    "LARGE_SALE,REFUND_ISSUED,PRICE_CHANGED,DISCOUNT_GIVEN,LOW_STOCK,OUT_OF_STOCK," +
        "CREDIT_GIVEN,CREDIT_LIMIT,DAY_CLOSED,CASH_SHORTAGE,PERMISSION_BLOCKED"

/** Every migration, in order. Registered on the database builder. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5
)
