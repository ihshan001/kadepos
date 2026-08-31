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
    val isActive: Boolean get() = providerEnabled
    val isConflict: Boolean get() = providerEnabled && ownerGmail.isBlank()
    fun requiresProviderContact(): Boolean = !providerEnabled

    companion object {
        const val DEFAULT_ACCESS_CODE_HINT = "Set by your POS provider"
    }
}
