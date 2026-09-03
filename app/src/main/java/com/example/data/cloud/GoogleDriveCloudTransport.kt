package com.example.data.cloud

import android.accounts.AccountManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal Google Drive REST transport, organised around a **shared shop folder**.
 *
 * Every device that belongs to the same shop writes into one folder
 * (`arro-pos-<shopKey>`) owned by whichever account created it, and every file
 * is named `<device>__<timestamp>.zip`. When a staff phone uploads under its
 * own Gmail, the file is additionally shared back to the hub (owner's main
 * Gmail) so the owner sees every device's snapshots hour by hour in one place.
 *
 * Uses the account already present on the device. The first upload from a
 * fresh install may ask the owner to approve Drive access through the Google
 * account chooser (handled by AccountManager through Google Play Services).
 */
class GoogleDriveCloudTransport(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()
    private val octet = "application/octet-stream".toMediaType()

    /** Gets an OAuth access token for the Drive.file scope for the chosen Gmail. */
    @Suppress("DEPRECATION")
    suspend fun accessToken(accountEmail: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val manager = AccountManager.get(context)
            val account = manager.getAccountsByType("com.google")
                .firstOrNull { it.name.equals(accountEmail, ignoreCase = true) }
                ?: return@withContext null
            manager.blockingGetAuthToken(
                account,
                "oauth2:https://www.googleapis.com/auth/drive.file",
                true
            )
        }.getOrNull()
    }

    /**
     * Finds or creates the shared folder for this shop. One folder per shop, so
     * every device's uploads land side by side.
     */
    suspend fun ensureShopFolder(token: String, shopKey: String): String = withContext(Dispatchers.IO) {
        val safeName = folderName(shopKey)
        queryFolder(token, safeName) ?: createFolder(token, safeName)
    }

    /**
     * Uploads one backup into the shop folder. When [shareWith] is another
     * account (the hub), the uploaded file is granted writer access to that
     * account so the owner can see and open it from their own Drive.
     */
    suspend fun upload(
        token: String,
        shopKey: String,
        deviceName: String,
        fileName: String,
        file: File,
        uploaderEmail: String = "",
        shareWith: String = ""
    ): FileMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = ensureShopFolder(token, shopKey)
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("mimeType", "application/zip")
                put("parents", JSONArray().put(folderId))
                put("appProperties", JSONObject().apply {
                    put("device", deviceName)
                    put("account", uploaderEmail)
                })
            }.toString()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", metadata)
                .addFormDataPart("media", file.name, file.asRequestBody(octet))
                .build()
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            val uploaded = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Upload failed ${response.code}: ${response.body?.string().orEmpty()}")
                }
                FileMetadata.fromJson(response.body?.string().orEmpty())
            }
            // A staff phone writes under its own account; hand the file to the
            // hub so the owner's Drive shows it too.
            if (shareWith.isNotBlank() && !shareWith.equals(uploaderEmail, ignoreCase = true)) {
                grantWriterAccess(token, uploaded.id, shareWith)
            }
            uploaded
        }.getOrNull()
    }

    /**
     * The snapshots currently in this shop's folder, newest first. This is the
     * hour-by-hour record: each row is a timestamped copy from one device.
     */
    suspend fun listSnapshots(token: String, shopKey: String, limit: Int = 50): List<DriveFileInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val folderId = ensureShopFolder(token, shopKey)
                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=${url("'$folderId' in parents and trashed=false and mimeType = 'application/zip'")}&orderBy=modifiedTime desc&pageSize=$limit&fields=files(id,name,modifiedTime,appProperties)")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("List failed ${response.code}")
                    val arr = JSONObject(response.body?.string().orEmpty()).optJSONArray("files") ?: JSONArray()
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        DriveFileInfo(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            modifiedTime = o.optString("modifiedTime")
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    /**
     * Downloads the newest backup in this shop's folder. Returns the bytes and
     * file id, or null when the folder is still empty.
     */
    suspend fun downloadLatest(token: String, shopKey: String): DownloadedFile? = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = ensureShopFolder(token, shopKey)
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${url("'$folderId' in parents and trashed=false and mimeType = 'application/zip'")}&orderBy=modifiedTime desc&pageSize=1&fields=files(id,name,modifiedTime)")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val meta = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("List failed ${response.code}")
                JSONObject(response.body?.string().orEmpty()).optJSONArray("files")?.optJSONObject(0)
            } ?: return@withContext null
            val id = meta.getString("id")
            val download = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$id?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val bytes = client.newCall(download).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Download failed ${response.code}")
                response.body?.bytes() ?: ByteArray(0)
            }
            DownloadedFile(id, meta.optString("name"), meta.optString("modifiedTime"), bytes)
        }.getOrNull()
    }

    /**
     * Grants [email] writer access to a file this app created. Best effort —
     * a failure here must never fail the backup itself.
     */
    private fun grantWriterAccess(token: String, fileId: String, email: String) {
        runCatching {
            val body = JSONObject().apply {
                put("role", "writer")
                put("type", "user")
                put("emailAddress", email)
            }.toString().toRequestBody(json)
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions?sendNotificationEmail=false")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Share failed ${response.code}")
                }
            }
        }
    }

    private fun queryFolder(token: String, name: String): String? {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=${url("name = '$name' and mimeType = 'application/vnd.google-apps.folder' and trashed=false")}&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else JSONObject(response.body?.string().orEmpty())
                .optJSONArray("files")?.optJSONObject(0)?.optString("id")
        }
    }

    private fun createFolder(token: String, name: String): String {
        val body = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString().toRequestBody(json)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Create folder failed ${response.code}: ${response.body?.string().orEmpty()}")
            JSONObject(response.body?.string().orEmpty()).getString("id")
        }
    }

    private fun folderName(shopKey: String): String =
        "arro-pos-${shopKey.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').ifBlank { "shop" }}"

    private fun url(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    data class FileMetadata(
        val id: String,
        val name: String
    ) {
        companion object {
            fun fromJson(raw: String): FileMetadata {
                val o = JSONObject(raw)
                return FileMetadata(o.optString("id"), o.optString("name"))
            }
        }
    }

    data class DriveFileInfo(
        val id: String,
        val name: String,
        val modifiedTime: String
    )

    data class DownloadedFile(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val bytes: ByteArray
    )
}
