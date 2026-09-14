package com.volp.travelbudget.data.receipt

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.data.update.UpdateChecker
import com.volp.travelbudget.domain.receipt.ReceiptParsing
import com.volp.travelbudget.domain.receipt.ReceiptReader
import com.volp.travelbudget.domain.receipt.ReceiptReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 영수증 사진을 Gemini에게 읽힌다.
 *
 * 사진은 이 요청에만 쓰인다. 다만 **기기 밖으로 나가는 일**이므로, 사용자가 버튼을 눌렀을 때만
 * 보내고 저절로 보내지 않는다. 키가 없는 빌드에서는 이 기능 자체가 꺼진다.
 */
class GeminiReceiptReader(
    private val context: Context,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val client: OkHttpClient = UpdateChecker.defaultClient(),
) : ReceiptReader {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun read(image: ByteArray, mimeType: String): ReceiptReading? =
        withContext(Dispatchers.IO) {
            if (!isConfigured || image.isEmpty()) return@withContext null

            val body = requestBody(image, mimeType)
            val request = Request.Builder()
                .url("$ENDPOINT?key=$apiKey")
                .post(body.toString().toRequestBody(JSON))
                .build()

            val raw = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull() ?: return@withContext null

            parse(raw)
        }

    /** 갤러리나 앱 저장소에 있는 사진을 읽어 보낸다. */
    suspend fun read(uri: Uri): ReceiptReading? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null

        read(bytes, "image/jpeg")
    }

    suspend fun read(file: File): ReceiptReading? = withContext(Dispatchers.IO) {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
        read(bytes, "image/jpeg")
    }

    private fun requestBody(image: ByteArray, mimeType: String): JSONObject {
        val parts = JSONArray()
            .put(JSONObject().put("text", ReceiptParsing.INSTRUCTION))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", mimeType)
                        .put("data", Base64.encodeToString(image, Base64.NO_WRAP)),
                ),
            )

        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            // 값을 옮겨 적는 일이라 지어낼 여지를 줄인다.
            .put("generationConfig", JSONObject().put("temperature", 0))
    }

    /** 답에서 글자를 꺼내 값으로 옮긴다. 모양이 조금 달라도 터지지 않게 한 겹씩 확인한다. */
    private fun parse(raw: String): ReceiptReading? {
        val text = runCatching {
            JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
        }.getOrNull() ?: return null

        val json = ReceiptParsing.extractJson(text) ?: return null
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return null

        return ReceiptReading(
            merchant = parsed.optString("merchant").trim(),
            total = ReceiptParsing.parseAmount(parsed.opt("total")?.toString()),
            currencyCode = ReceiptParsing.normalizeCurrency(parsed.optString("currency")),
            date = ReceiptParsing.parseDate(parsed.optString("date")),
            confidence = parsed.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
        )
    }

    private companion object {
        const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
        val JSON = "application/json".toMediaType()
    }
}
