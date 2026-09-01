package com.example.data.cloud

/**
 * Per-device cloud/backup policy.
 *
 * A device connects its own Google account (normally the owner's Gmail) and is
 * responsible for its own snapshot. The provider controls the master switch and
 * the hourly/manual policy; the owner can only connect an account and press
 * "Backup now" / "Sync now".
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
    // Owner-visible, owner-editable.
    val ownerGmail: String = "",
    val deviceName: String = "",
    // Last run status.
    val lastBackupAt: Long = 0L,
    val lastSyncAt: Long = 0L,
    val lastBackupFile: String = "",
    val lastUploadedFile: String = "",
    val lastUploadedHash: String = "",
    val lastError: String = "",
    val pendingChanges: Int = 0,
    val accountConnected: Boolean = false
) {
    /** True only when the provider allowed it *and* the shop left it on. */
    val isActive: Boolean get() = providerEnabled && ownerBackupEnabled
    val isConflict: Boolean get() = providerEnabled && ownerGmail.isBlank()
    fun requiresProviderContact(): Boolean = !providerEnabled
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
    }
}
