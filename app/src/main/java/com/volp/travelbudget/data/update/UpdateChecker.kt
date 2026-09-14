package com.volp.travelbudget.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.volp.travelbudget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** GitHub 릴리스 한 건에서 뽑아낸 정보. */
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String,
    /** 사람이 열어 보는 릴리스 페이지. 앱이 직접 설치하지 못하는 빌드에서 이쪽으로 보낸다. */
    val pageUrl: String,
)

/**
 * GitHub Actions가 올린 최신 APK를 확인하고 내려받는다.
 *
 * 릴리스 태그는 워크플로에서 `v<versionName>` 형식으로 만들고, 마지막 자리를 버전 코드로 쓴다.
 * (예: `v1.0.42` → 42)
 */
class UpdateChecker(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            parseRelease(body)
        }
    }

    /** 이 릴리스가 지금 설치된 버전보다 새것인지. */
    fun isNewer(release: ReleaseInfo): Boolean = release.versionCode > BuildConfig.VERSION_CODE

    suspend fun download(
        release: ReleaseInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply {
            // 예전에 받아 둔 파일이 쌓이지 않게 매번 비운다.
            deleteRecursively()
            mkdirs()
        }
        val target = File(directory, "volp-${release.versionName}.apk")

        val request = Request.Builder().url(release.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                error("다운로드에 실패했다 (HTTP ${response.code})")
            }
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target
    }

    /**
     * 릴리스 페이지를 브라우저로 연다.
     *
     * 설치 권한이 없는 빌드에서는 앱이 APK를 직접 넘길 수 없다. 사람이 브라우저에서 받아
     * 설치하는 편이 권한을 더 받는 것보다 낫다.
     */
    fun openReleasePage(release: ReleaseInfo) {
        val url = release.pageUrl.ifBlank {
            "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 설치 화면을 띄운다. 사용자가 마지막으로 한 번 더 확인하게 된다. */
    fun install(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** '출처를 알 수 없는 앱 설치' 권한이 켜져 있는지. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 위 권한을 켜는 설정 화면. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun parseRelease(body: String): ReleaseInfo? {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").removePrefix("v")
        if (tag.isBlank()) return null
        val versionCode = tag.substringAfterLast('.').toIntOrNull() ?: return null

        val assets = json.optJSONArray("assets") ?: return null
        val apks = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            .filter { it.optString("browser_download_url").isNotBlank() }

        // 한 릴리스에 빌드가 둘 올라간다. 지금 깔린 것과 같은 갈래를 고르지 않으면 권한이 다른
        // 앱으로 갈아타게 된다. 자동 수집판만 이름에 표시가 있고, 안전판은 예전과 같은 이름을
        // 쓴다. 갈래를 모르던 옛 버전이 첫 APK를 집어 가도 안전판이 잡히게 하기 위해서다.
        val mine = apks.firstOrNull { asset ->
            val name = asset.optString("name")
            if (BuildConfig.CAN_CAPTURE) {
                name.contains(FULL_MARKER, ignoreCase = true)
            } else {
                !name.contains(FULL_MARKER, ignoreCase = true)
            }
        }
        val asset = mine ?: apks.firstOrNull() ?: return null

        return ReleaseInfo(
            versionCode = versionCode,
            versionName = tag,
            downloadUrl = asset.optString("browser_download_url"),
            sizeBytes = asset.optLong("size"),
            notes = json.optString("body"),
            pageUrl = json.optString("html_url"),
        )
    }

    companion object {
        private const val DOWNLOAD_BUFFER = 16 * 1024

        /** 자동 수집판 APK 이름에 붙는 표시. 안전판에는 아무 표시도 붙이지 않는다. */
        private const val FULL_MARKER = "-full-"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
