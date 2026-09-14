package com.volp.travelbudget.data.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.volp.travelbudget.MainActivity
import com.volp.travelbudget.R
import com.volp.travelbudget.data.local.CaptureSource
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.domain.cardsms.CardMessageParser
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 문자와 앱 알림에서 읽은 결제를 미확인함에 넣고, 사용자에게 알린다.
 *
 * 문자와 알림이 같은 결제를 동시에 물고 오는 경우가 흔해 [TripRepository.capture]에서
 * 지문으로 한 건만 남긴다.
 */
class CardCaptureHandler(
    private val context: Context,
    private val repository: TripRepository,
    private val settings: AppSettings,
) {

    suspend fun handle(
        body: String,
        sender: String?,
        source: CaptureSource,
        receivedAtMillis: Long = System.currentTimeMillis(),
    ) {
        val current = settings.settings.first()
        if (!current.captureEnabled) return

        val receivedAt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(receivedAtMillis),
            ZoneId.systemDefault(),
        )
        val transaction = CardMessageParser.parse(body, receivedAt, sender) ?: return
        val stored = repository.capture(transaction, source, current.ownerName)
        if (!stored) return

        val amount = if (transaction.isOverseas) {
            formatForeign(transaction.amount, transaction.currencyCode)
        } else {
            formatKrw(transaction.amount.toLong())
        }
        notify(
            title = "${transaction.issuer.label} $amount",
            text = "${transaction.merchant} · 어느 여행의 지출인지 정해 주세요",
        )
    }

    private fun notify(title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "카드 결제 수집",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "카드 문자에서 읽은 결제를 알려 준다"
            },
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_INBOX, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "card_capture"
        const val NOTIFICATION_ID = 1001
    }
}
