package com.volp.travelbudget.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Drive REST API 중 백업에 필요한 부분만 감싼 얇은 클라이언트.
 *
 * `drive.file` 범위만 쓴다. 이 앱이 만든 파일과 폴더에만 접근할 수 있어, 사용자의 다른
 * 드라이브 파일은 건드리지 못한다.
 */
class DriveClient(private val client: OkHttpClient) {

    suspend fun findFolder(token: String, name: String): String? = withContext(Dispatchers.IO) {
        val escaped = name.replace("'", "\\'")
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter(
                "q",
                "name = '$escaped' and mimeType = '$FOLDER_MIME' and trashed = false",
            )
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("spaces", "drive")
            .build()
        val json = getJson(token, url.toString())
        json?.optJSONArray("files")?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("id")
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun createFolder(token: String, name: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .put("mimeType", FOLDER_MIME)
            .toString()
            .toRequestBody(JSON_MIME)

        val request = Request.Builder()
            .url("$API/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("폴더를 만들지 못했다 (HTTP ${response.code})")
            JSONObject(text).getString("id")
        }
    }

    suspend fun findFile(token: String, folderId: String, name: String): String? =
        withContext(Dispatchers.IO) {
            val escaped = name.replace("'", "\\'")
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "name = '$escaped' and '$folderId' in parents and trashed = false")
                .addQueryParameter("fields", "files(id,name)")
                .addQueryParameter("spaces", "drive")
                .build()
            getJson(token, url.toString())
                ?.optJSONArray("files")
                ?.takeIf { it.length() > 0 }
                ?.getJSONObject(0)
                ?.optString("id")
                ?.takeIf { it.isNotBlank() }
        }

    /** 같은 이름의 파일이 있으면 내용을 덮어쓰고, 없으면 새로 만든다. */
    suspend fun uploadText(
        token: String,
        folderId: String,
        name: String,
        mimeType: String,
        content: String,
    ): String = withContext(Dispatchers.IO) {
        val existingId = findFile(token, folderId, name)
        val media: RequestBody = content.toRequestBody(mimeType.toMediaType())

        val request = if (existingId != null) {
            Request.Builder()
                .url("$UPLOAD/files/$existingId?uploadType=media&fields=id")
                .header("Authorization", "Bearer $token")
                .patch(media)
                .build()
        } else {
            val metadata = JSONObject()
                .put("name", name)
                .put("parents", JSONArray().put(folderId))
                .toString()
                .toRequestBody(JSON_MIME)
            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata)
                .addPart(media)
                .build()
            Request.Builder()
                .url("$UPLOAD/files?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()
        }

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("$name 을 올리지 못했다 (HTTP ${response.code})")
            JSONObject(text).optString("id")
        }
    }

    suspend fun downloadText(token: String, fileId: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("백업 파일을 읽지 못했다 (HTTP ${response.code})")
            text
        }
    }

    private fun getJson(token: String, url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("드라이브를 읽지 못했다 (HTTP ${response.code})")
            text.takeIf { it.isNotBlank() }?.let(::JSONObject)
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        val JSON_MIME = "application/json; charset=UTF-8".toMediaType()
    }
}
