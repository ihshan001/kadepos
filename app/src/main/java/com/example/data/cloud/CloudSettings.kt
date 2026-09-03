package com.example.data.cloud

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-device cloud/backup policy.
 *
 * A shop has **one hub Gmail** (the owner's main account) that is the single
 * store of the whole shop's data on Google Drive, plus any number of **linked
 * staff Gmails**. A cashier never needs the owner's password: they connect
 * their *own* Gmail on their phone, and the app writes that phone's backups
 * into the same shared shop folder and shares them back to the hub account, so
 * the owner sees every device's data hour by hour in one place.
 *
 * Everything here is device-local preferences, not a Room table, so an upgrade
 * never depends on a migration to keep working.
 */
data class CloudSettings(
    // Provider master control. The owner cannot flip these.
    val providerEnabled: Boolean = false,
    val providerEmail: String = "",
    val providerAccessCodeHash: String = "",
    val providerAccessCodeHint: String = "",
    val hourlySyncEnabled: Boolean = true,
    val dailyBackupEnabled: Boolean = true,
    val keepBackupDays: Int = 30,
    /**
     * The shop's own switch. The provider decides whether this device is
     * *allowed* to back up; the owner decides whether it actually does. Both
     * must be on for a copy to be made.
     */
    val ownerBackupEnabled: Boolean = true,
    /**
     * The Gmail this device signs in with. This is normally the owner's main
     * account, but a staff phone signs in with the staff member's own Gmail.
     */
    val ownerGmail: String = "",
    /**
     * The owner's main Gmail — the canonical store of the whole shop's data.
     * When blank the device falls back to [ownerGmail], which keeps a solo
     * shop (one phone, one account) working with no extra setup.
     */
    val hubGmail: String = "",
    /**
     * Staff Gmails the owner has linked, comma separated. Each staff phone
     * signs in with its own linked account instead of the owner's.
     */
    val linkedGmails: String = "",
    /**
     * A stable folder key for this shop, derived from the shop name. All
     * devices that belong to the same shop write into the same shared Drive
     * folder `arro-pos-<shopKey>`.
     */
    val shopKey: String = "",
    /** Cached id of the shared Drive folder, so a sync does not search twice. */
    val sharedFolderId: String = "",
    /** Owner-visible, owner-editable. */
    val deviceName: String = "",
    // Last run status.
    val lastBackupAt: Long = 0L,
    val lastSyncAt: Long = 0L,
    val lastBackupFile: String = "",
    val lastUploadedFile: String = "",
    val lastUploadedHash: String = "",
    val lastError: String = "",
    val pendingChanges: Int = 0,
    val accountConnected: Boolean = false,
    /**
     * A small rolling record of the most recent syncs, so the owner can see
     * the shop's data arriving hour by hour even when offline. Encoded as a
     * JSON array; see [syncEvents].
     */
    val syncHistory: String = ""
) {
    /** True only when the provider allowed it *and* the shop left it on. */
    val isActive: Boolean get() = providerEnabled && ownerBackupEnabled

    val isConflict: Boolean get() = providerEnabled && ownerGmail.isBlank()

    fun requiresProviderContact(): Boolean = !providerEnabled

    /** The account that is the single store of the whole shop's data. */
    fun hub(): String = hubGmail.ifBlank { ownerGmail }

    /** Staff Gmails linked to this shop's backup, as a clean list. */
    fun linkedEmails(): List<String> =
        linkedGmails.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()

    fun withLinkedEmail(email: String): CloudSettings {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return this
        return copy(linkedGmails = (linkedEmails() + clean).distinct().joinToString(","))
    }

    fun withoutLinkedEmail(email: String): CloudSettings {
        val clean = email.trim().lowercase()
        return copy(linkedGmails = linkedEmails().filterNot { it.equals(clean, true) }.joinToString(","))
    }

    /** The recent syncs, newest first. Never more than [MAX_HISTORY]. */
    fun syncEvents(): List<CloudSyncEvent> {
        if (syncHistory.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(syncHistory)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                CloudSyncEvent(
                    at = o.optLong("at", 0L),
                    device = o.optString("device", ""),
                    account = o.optString("account", ""),
                    fileName = o.optString("fileName", ""),
                    ok = o.optBoolean("ok", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun withSyncEvent(event: CloudSyncEvent): CloudSettings {
        val events = (listOf(event) + syncEvents()).take(MAX_HISTORY)
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("at", e.at)
                    put("device", e.device)
                    put("account", e.account)
                    put("fileName", e.fileName)
                    put("ok", e.ok)
                }
            )
        }
        return copy(syncHistory = arr.toString())
    }

    /** Why nothing is being copied right now, in plain words. */
    fun statusLine(): String = when {
        !providerEnabled -> "Not switched on by your POS provider yet"
        !ownerBackupEnabled -> "Switched off on this phone"
        ownerGmail.isBlank() -> "Add your Google mail to start copying"
        lastError.isNotBlank() -> "Needs attention"
        else -> "On — bills and stock are being copied"
    }

    companion object {
        const val DEFAULT_ACCESS_CODE_HINT = "Set by your POS provider"
        const val MAX_HISTORY = 50
    }
}

/**
 * One line of the rolling sync history: what was copied, by which device and
 * account, and whether it made it.
 */
data class CloudSyncEvent(
    val at: Long,
    val device: String,
    val account: String,
    val fileName: String,
    val ok: Boolean
)
