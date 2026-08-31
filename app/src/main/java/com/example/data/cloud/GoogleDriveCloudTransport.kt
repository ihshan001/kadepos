package com.example.data.cloud

import android.accounts.AccountManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal Google Drive REST transport.
 *
 * Uses the account already present on the device (normally the owner's Gmail).
 * The first slice keeps a per-device folder: "kadepos-device/<deviceName>" so
 * two phones signing in with the same Google account do not overwrite each
 * other.
 *
 * Note: the first upload from a fresh Android install may ask the owner to
 * approve access to Google Drive through the Google account chooser. This is
 * handled by AccountManager through Google Play Services.
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

    suspend fun ensureDeviceFolder(token: String, deviceName: String): String = withContext(Dispatchers.IO) {
        val safeName = "kadepos-device-${deviceName.ifBlank { "unknown" }}".replace(
            Regex("[^A-Za-z0-9._-]"), "-"
        )
        val existing = queryFolder(token, safeName)
        existing ?: createFolder(token, safeName)
    }

    suspend fun upload(
        token: String,
        deviceName: String,
        fileName: String,
        file: File
    ): FileMetadata? = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = ensureDeviceFolder(token, deviceName)
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("mimeType", "application/zip")
                put("parents", JSONArray().put(folderId))
            }.toString()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", metadata)
                .addFormDataPart("media", file.name, file.inputStream().readBytes().toRequestBody(octet))
                .build()
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Upload failed ${response.code}: ${response.body?.string().orEmpty()}")
                }
                FileMetadata.fromJson(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /**
     * Downloads the newest backup in this device's Drive folder. Returns the
     * bytes and file id, or null when the folder is still empty.
     */
    suspend fun downloadLatest(token: String, deviceName: String): DownloadedFile? = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = ensureDeviceFolder(token, deviceName)
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=${url("'$folderId' in parents and trashed=false")}&orderBy=modifiedTime desc&pageSize=1&fields=files(id,name,modifiedTime)")
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

    data class DownloadedFile(
        val id: String,
        val name: String,
        val modifiedTime: String,
        val bytes: ByteArray
    )
}
