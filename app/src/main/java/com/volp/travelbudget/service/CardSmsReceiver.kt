package com.volp.travelbudget.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.volp.travelbudget.VolpApplication
import com.volp.travelbudget.data.local.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 카드사 결제 문자를 받는다.
 *
 * 긴 문자는 여러 조각으로 쪼개져 오므로 발신번호별로 이어 붙인 뒤에 파서에 넘긴다.
 */
class CardSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val application = context.applicationContext as? VolpApplication ?: return
        val sender = messages.first().originatingAddress
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = messages.first().timestampMillis.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        // 방송 수신자는 짧게 끝나야 하므로 실제 저장은 별도 스코프에서 처리한다.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                application.captureHandler.handle(body, sender, CaptureSource.SMS, receivedAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
