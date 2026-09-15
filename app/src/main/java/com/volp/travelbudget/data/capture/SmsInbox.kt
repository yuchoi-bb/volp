package com.volp.travelbudget.data.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 문자함에서 꺼낸 문자 한 통. */
data class InboxMessage(
    val body: String,
    val sender: String?,
    val receivedAt: LocalDateTime,
)

/**
 * 기기 문자함을 날짜로 훑는다.
 *
 * 지난 여행의 지출은 그때 받은 결제 문자에 남아 있다. 여행 기간만 골라 읽으므로 그 밖의 문자는
 * 건드리지 않는다. 문자 권한이 없는 빌드에서는 늘 빈 목록이다.
 */
class SmsInbox(private val context: Context) {

    val canRead: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** @param to 이 날까지 포함해서 읽는다. */
    suspend fun messagesBetween(from: LocalDate, to: LocalDate): List<InboxMessage> =
        withContext(Dispatchers.IO) {
            if (!canRead) return@withContext emptyList()

            val zone = ZoneId.systemDefault()
            val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            )

            val messages = mutableListOf<InboxMessage>()
            runCatching {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
                    arrayOf(start.toString(), end.toString()),
                    "${Telephony.Sms.DATE} ASC",
                )?.use { cursor ->
                    val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                    while (cursor.moveToNext()) {
                        val body = cursor.getString(bodyIndex) ?: continue
                        messages += InboxMessage(
                            body = body,
                            sender = cursor.getString(addressIndex),
                            receivedAt = LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(cursor.getLong(dateIndex)),
                                zone,
                            ),
                        )
                    }
                }
            }
            messages
        }
}
