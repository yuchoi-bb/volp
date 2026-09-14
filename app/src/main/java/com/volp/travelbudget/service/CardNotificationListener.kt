package com.volp.travelbudget.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.volp.travelbudget.VolpApplication
import com.volp.travelbudget.data.local.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 카드사 앱이 띄우는 결제 알림을 읽는다.
 *
 * 요즘은 문자 대신 앱 푸시로만 오는 결제가 많아 문자 수신과 함께 쓴다. 같은 결제가 양쪽으로
 * 들어와도 미확인함에서 한 건으로 합쳐진다.
 */
class CardNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val application = applicationContext as? VolpApplication ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()

        // 알림은 제목과 본문이 나뉘어 있어 문자 한 통처럼 이어 붙인다.
        val body = listOf(title, bigText.ifBlank { text })
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (body.isBlank()) return

        scope.launch {
            application.captureHandler.handle(
                body = body,
                sender = null,
                source = CaptureSource.NOTIFICATION,
                receivedAtMillis = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
