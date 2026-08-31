package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The kinds of thing the shop can be told about. Each one can be switched on
 * or off independently, because a busy grocery does not want a buzz for every
 * single sale while a jeweller very much does.
 */
enum class NotificationType(
    val key: String,
    val title: String,
    val description: String,
    /** Only people with this permission are told. Null means everyone. */
    val requires: Permission?,
    /** Sensible default for a new shop. */
    val defaultOn: Boolean,
    /** Owner-only alerts stay on even when the shop is quiet. */
    val importance: NotificationImportance
) {
    SALE_COMPLETED(
        key = "SALE_COMPLETED",
        title = "Every sale",
        description = "A note each time a bill is completed, with the amount and who sold it",
        requires = Permission.VIEW_SALES_HISTORY,
        defaultOn = false,
        importance = NotificationImportance.QUIET
    ),
    LARGE_SALE(
        key = "LARGE_SALE",
        title = "Big sales",
        description = "Only tells you when a bill is over the amount you set",
        requires = Permission.VIEW_SALES_HISTORY,
        defaultOn = true,
        importance = NotificationImportance.NORMAL
    ),
    REFUND_ISSUED(
        key = "REFUND_ISSUED",
        title = "Refunds",
        description = "Whenever money is handed back to a customer",
        requires = Permission.VIEW_REPORTS,
        defaultOn = true,
        importance = NotificationImportance.HIGH
    ),
    PRICE_CHANGED(
        key = "PRICE_CHANGED",
        title = "Price changed at the counter",
        description = "When staff sell something for less or more than the listed price",
        requires = Permission.VIEW_PROFIT,
        defaultOn = true,
        importance = NotificationImportance.HIGH
    ),
    DISCOUNT_GIVEN(
        key = "DISCOUNT_GIVEN",
        title = "Large discounts",
        description = "When a discount above your limit is given on a bill",
        requires = Permission.VIEW_PROFIT,
        defaultOn = true,
        importance = NotificationImportance.NORMAL
    ),
    LOW_STOCK(
        key = "LOW_STOCK",
        title = "Stock running low",
        description = "When an item drops to its reorder level",
        requires = Permission.MANAGE_INVENTORY,
        defaultOn = true,
        importance = NotificationImportance.NORMAL
    ),
    OUT_OF_STOCK(
        key = "OUT_OF_STOCK",
        title = "Item finished",
        description = "When an item reaches zero and cannot be sold",
        requires = Permission.MANAGE_INVENTORY,
        defaultOn = true,
        importance = NotificationImportance.HIGH
    ),
    CREDIT_GIVEN(
        key = "CREDIT_GIVEN",
        title = "Goods taken on credit",
        description = "When a customer takes goods without paying",
        requires = Permission.MANAGE_CUSTOMERS,
        defaultOn = true,
        importance = NotificationImportance.NORMAL
    ),
    CREDIT_LIMIT(
        key = "CREDIT_LIMIT",
        title = "Customer over their limit",
        description = "When someone owes more than the limit you set for them",
        requires = Permission.MANAGE_CUSTOMERS,
        defaultOn = true,
        importance = NotificationImportance.HIGH
    ),
    DAY_CLOSED(
        key = "DAY_CLOSED",
        title = "Day closed",
        description = "A summary when the till is closed, including any cash difference",
        requires = Permission.MANAGE_CASH,
        defaultOn = true,
        importance = NotificationImportance.NORMAL
    ),
    CASH_SHORTAGE(
        key = "CASH_SHORTAGE",
        title = "Cash does not match",
        description = "When the counted cash differs from what the app expected",
        requires = Permission.MANAGE_CASH,
        defaultOn = true,
        importance = NotificationImportance.HIGH
    ),
    STAFF_SIGN_IN(
        key = "STAFF_SIGN_IN",
        title = "Staff sign in",
        description = "When someone signs in or out at the counter",
        requires = Permission.MANAGE_STAFF,
        defaultOn = false,
        importance = NotificationImportance.QUIET
    ),
    PERMISSION_BLOCKED(
        key = "PERMISSION_BLOCKED",
        title = "Blocked attempts",
        description = "When staff try something they are not allowed to do",
        requires = Permission.VIEW_AUDIT,
        defaultOn = true,
        importance = NotificationImportance.HIGH
    );

    companion object {
        fun fromKey(key: String): NotificationType? =
            entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }

        /** Only the types this person is entitled to be told about. */
        fun visibleTo(permissions: PermissionSet): List<NotificationType> =
            entries.filter { it.requires == null || permissions.can(it.requires) }
    }
}

/** How loudly something should be surfaced. */
enum class NotificationImportance { QUIET, NORMAL, HIGH }

/**
 * One thing that happened, kept so the owner can scroll back through the day
 * even if they were not holding the phone at the time. This is the record;
 * whether it also buzzes the phone is a separate decision.
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val body: String,
    val amount: Double = 0.0,
    /** Who caused it, so "Nimal gave a 500 discount" reads properly. */
    val actorName: String = "",
    /** Invoice number, product name or similar, for jumping to the record. */
    val reference: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val importance: String = NotificationImportance.NORMAL.name
)

/**
 * The shop's choices about what to be told. Stored as one row rather than a
 * row per type so reading the settings is a single cheap query on the hot path
 * of completing a sale.
 *
 * [enabledKeys] is a comma separated list of NotificationType keys. Unknown
 * names are ignored, so an older row can never break a newer build.
 */
@Entity(tableName = "notification_settings")
data class NotificationSettingsEntity(
    @PrimaryKey val id: Int = 1,
    /** Master switch. Off means the app never interrupts anyone. */
    val enabled: Boolean = true,
    val enabledKeys: String = NotificationType.entries
        .filter { it.defaultOn }
        .joinToString(",") { it.key },
    /** A bill at or above this is a "big sale". */
    val largeSaleThreshold: Double = 10_000.0,
    /** A discount at or above this is worth telling the owner about. */
    val largeDiscountThreshold: Double = 500.0,
    /** Do not buzz outside shop hours; the entry is still recorded. */
    val quietHoursEnabled: Boolean = false,
    val quietFromHour: Int = 21,
    val quietToHour: Int = 7
) {
    fun isOn(type: NotificationType): Boolean =
        enabled && enabledKeys.split(",").any { it.trim().equals(type.key, ignoreCase = true) }

    fun withType(type: NotificationType, on: Boolean): NotificationSettingsEntity {
        val current = enabledKeys.split(",")
            .mapNotNull { NotificationType.fromKey(it) }
            .toMutableSet()
        if (on) current.add(type) else current.remove(type)
        return copy(enabledKeys = current.joinToString(",") { it.key })
    }

    /** True when we should stay silent right now (the entry is still saved). */
    fun isQuietAt(hourOfDay: Int): Boolean {
        if (!quietHoursEnabled) return false
        // Handles windows that cross midnight, e.g. 21:00 to 07:00.
        return if (quietFromHour <= quietToHour) {
            hourOfDay in quietFromHour until quietToHour
        } else {
            hourOfDay >= quietFromHour || hourOfDay < quietToHour
        }
    }
}
