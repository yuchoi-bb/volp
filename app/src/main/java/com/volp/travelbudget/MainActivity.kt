package com.volp.travelbudget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.volp.travelbudget.ui.VolpApp
import com.volp.travelbudget.ui.theme.VolpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 결제 알림을 눌러 들어온 경우 바로 미확인함을 연다.
            var openInbox by remember { mutableStateOf(intent?.getBooleanExtra(EXTRA_OPEN_INBOX, false) == true) }
            VolpTheme {
                VolpApp(
                    openInbox = openInbox,
                    onInboxOpened = { openInbox = false },
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
        const val EXTRA_OPEN_INBOX = "open_inbox"
    }
}
