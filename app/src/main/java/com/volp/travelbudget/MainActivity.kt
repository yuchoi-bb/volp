package com.volp.travelbudget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.volp.travelbudget.ui.StartTarget
import com.volp.travelbudget.ui.VolpApp
import com.volp.travelbudget.ui.theme.VolpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 알림이나 홈 화면 바로가기로 들어온 경우 해당 화면을 바로 연다.
            var target by remember { mutableStateOf(startTargetOf(intent)) }
            VolpTheme {
                VolpApp(
                    startTarget = target,
                    onStartTargetHandled = { target = StartTarget.None },
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
        const val EXTRA_OPEN_TRIP_ID = "open_trip_id"
        const val EXTRA_QUICK_ENTRY = "quick_entry"

        /**
         * 인텐트를 보고 처음 열 화면을 정한다.
         *
         * 홈 화면 바로가기는 값을 문자열로만 넘길 수 있어 두 가지를 모두 본다.
         */
        fun startTargetOf(intent: Intent?): StartTarget {
            if (intent == null) return StartTarget.None

            val quick = intent.getBooleanExtra(EXTRA_QUICK_ENTRY, false) ||
                intent.getStringExtra(EXTRA_QUICK_ENTRY) == "true"
            if (quick) return StartTarget.QuickEntry

            if (intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)) return StartTarget.Inbox

            val tripId = intent.getLongExtra(EXTRA_OPEN_TRIP_ID, 0L)
            return if (tripId > 0L) StartTarget.Trip(tripId) else StartTarget.None
        }
    }
}
