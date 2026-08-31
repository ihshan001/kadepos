package com.example.data.cloud

import android.accounts.AccountManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores the per-device cloud policy in SharedPreferences. Kept deliberately
 * small: the rest of the app uses Room for business data and this file only
 * stores policy/status for the optional cloud feature.
 */
class CloudSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): CloudSettings {
        val raw = prefs.getString(KEY_JSON, null) ?: return CloudSettings()
        return runCatching {
            val o = JSONObject(raw)
            CloudSettings(
                providerEnabled = o.optBoolean("providerEnabled", false),
                providerEmail = o.optString("providerEmail", ""),
                providerAccessCodeHash = o.optString("providerAccessCodeHash", ""),
                providerAccessCodeHint = o.optString("providerAccessCodeHint", ""),
                hourlySyncEnabled = o.optBoolean("hourlySyncEnabled", true),
                dailyBackupEnabled = o.optBoolean("dailyBackupEnabled", true),
                keepBackupDays = o.optInt("keepBackupDays", 30),
                ownerGmail = o.optString("ownerGmail", ""),
                deviceName = o.optString("deviceName", ""),
                lastBackupAt = o.optLong("lastBackupAt", 0L),
                lastSyncAt = o.optLong("lastSyncAt", 0L),
                lastBackupFile = o.optString("lastBackupFile", ""),
                lastUploadedFile = o.optString("lastUploadedFile", ""),
                lastUploadedHash = o.optString("lastUploadedHash", ""),
                lastError = o.optString("lastError", ""),
                pendingChanges = o.optInt("pendingChanges", 0),
                accountConnected = o.optBoolean("accountConnected", false)
            )
        }.getOrDefault(CloudSettings())
    }

    @Synchronized
    fun save(settings: CloudSettings) {
        val o = JSONObject().apply {
            put("providerEnabled", settings.providerEnabled)
            put("providerEmail", settings.providerEmail)
            put("providerAccessCodeHash", settings.providerAccessCodeHash)
            put("providerAccessCodeHint", settings.providerAccessCodeHint)
            put("hourlySyncEnabled", settings.hourlySyncEnabled)
            put("dailyBackupEnabled", settings.dailyBackupEnabled)
            put("keepBackupDays", settings.keepBackupDays)
            put("ownerGmail", settings.ownerGmail)
            put("deviceName", settings.deviceName)
            put("lastBackupAt", settings.lastBackupAt)
            put("lastSyncAt", settings.lastSyncAt)
            put("lastBackupFile", settings.lastBackupFile)
            put("lastUploadedFile", settings.lastUploadedFile)
            put("lastUploadedHash", settings.lastUploadedHash)
            put("lastError", settings.lastError)
            put("pendingChanges", settings.pendingChanges)
            put("accountConnected", settings.accountConnected)
        }
        prefs.edit().putString(KEY_JSON, o.toString()).apply()
    }

    @Synchronized
    fun update(transform: (CloudSettings) -> CloudSettings): CloudSettings {
        val next = transform(load())
        save(next)
        return next
    }

    fun setProviderCode(code: String): Boolean {
        if (code.isBlank()) return false
        val salt = prefs.getString(KEY_PROVIDER_SALT, null) ?: randomSalt().also {
            prefs.edit().putString(KEY_PROVIDER_SALT, it).apply()
        }
        val hash = hash(salt + "|" + code)
        update { it.copy(providerAccessCodeHash = hash, providerAccessCodeHint = code.take(2) + "…") }
        return true
    }

    fun verifyProviderCode(code: String, email: String): Boolean {
        if (code.isBlank()) return false
        val settings = load()
        if (settings.providerAccessCodeHash.isBlank()) return false
        // Once a provider Gmail is stored, the technician must enter it to
        // unlock. An empty email is not accepted on a configured device.
        if (settings.providerEmail.isNotBlank() &&
            !email.equals(settings.providerEmail, ignoreCase = true)
        ) return false
        val salt = prefs.getString(KEY_PROVIDER_SALT, "") ?: ""
        return settings.providerAccessCodeHash == hash(salt + "|" + code)
    }

    fun isProviderConfigured(): Boolean {
        val s = load()
        return s.providerEnabled || s.providerAccessCodeHash.isNotBlank()
    }

    /**
     * Stable per-device identifier. Two phones signed into the same Google
     * account still get separate Drive folders, which is the per-device rule.
     */
    fun deviceId(): String = runCatching {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull().orEmpty().ifBlank {
        runCatching {
            (Build.MODEL + Build.SERIAL).hashCode().toUInt().toString(16)
        }.getOrNull().orEmpty().ifBlank {
            "device-${System.currentTimeMillis().toString(16)}"
        }
    }

    /** Short name shown to the owner; the full Android id stays in the folder. */
    fun displayDeviceName(): String = load().deviceName.ifBlank { deviceId().take(8) }

    /** Google accounts already present on the device, so the owner does not have to type an address. */
    @Suppress("DEPRECATION")
    fun googleAccounts(): List<String> = runCatching {
        AccountManager.get(appContext)
            .getAccountsByType("com.google")
            .map { it.name }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFS_NAME = "kadepos_cloud_settings"
        private const val KEY_JSON = "cloud_json"
        private const val KEY_PROVIDER_SALT = "provider_salt"

        private fun randomSalt(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
