package com.volp.travelbudget.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.volp.travelbudget.ui.purchase.PurchaseEditorScreen
import com.volp.travelbudget.ui.theme.VolpTheme

/**
 * 다른 앱에서 보낸 글을 구매 기록으로 받는다.
 *
 * 두 갈래로 들어온다. 문자 앱에서 **공유**를 누르면 [Intent.ACTION_SEND]로, 글을 길게 눌러
 * 블록을 잡고 메뉴에서 고르면 [Intent.ACTION_PROCESS_TEXT]로 온다. 어느 쪽이든 글 한 덩어리를
 * 받아 읽어 낸 값을 채운 편집 화면을 띄운다.
 */
class ShareCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = sharedTextOf(intent)
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "받은 글이 비어 있습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            VolpTheme {
                PurchaseEditorScreen(
                    sharedText = text,
                    onDone = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    companion object {

        /** 두 가지 인텐트에서 글을 꺼낸다. 제목이 따로 오면 앞에 붙여 준다. */
        fun sharedTextOf(intent: Intent?): String? {
            if (intent == null) return null

            val body = when (intent.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                Intent.ACTION_PROCESS_TEXT ->
                    intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                        ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)?.toString()
                else -> null
            } ?: return null

            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            return if (subject.isNotBlank() && !body.contains(subject)) {
                "$subject\n$body"
            } else {
                body
            }
        }
    }
}
