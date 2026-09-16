package com.volp.travelbudget.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.volp.travelbudget.service.CardNotificationListener

/** 자동 수집에 필요한 권한이 지금 어떤 상태인지. */
data class CaptureAccess(
    val sms: Boolean,
    val notifications: Boolean,
) {
    val allGranted: Boolean get() = sms && notifications
}

/**
 * 문자·알림 권한 상태를 본다.
 *
 * 알림 접근은 시스템 설정 화면에서 켜고 돌아오는 것이라 앱이 결과를 받을 길이 없다. 그래서
 * 화면이 다시 보일 때마다 확인한다. 그러지 않으면 켜고 돌아와도 계속 꺼진 것으로 보인다.
 */
@Composable
fun rememberCaptureAccess(): CaptureAccess {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(readCaptureAccess(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = readCaptureAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return access
}

private fun readCaptureAccess(context: Context) = CaptureAccess(
    sms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED,
    notifications = Settings.Secure
        .getString(context.contentResolver, "enabled_notification_listeners")
        ?.contains(CardNotificationListener::class.java.name) == true,
)

/**
 * 아직 안 켠 권한을 켜는 단추들.
 *
 * 권한이 없으면 자동 수집은 한 통도 못 읽는다. 그런데 앱이 그 사실을 말하지 않으면 사용자는
 * 기능이 고장 났다고 여긴다. 그래서 미확인함과 설정 양쪽에 같은 안내를 둔다.
 */
@Composable
fun CapturePermissionButtons(
    access: CaptureAccess,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    Column(modifier.fillMaxWidth()) {
        if (!access.sms) {
            Text(
                "카드 결제 문자를 읽으려면 문자 권한이 필요하다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    smsLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("문자 권한 허용") }
            Spacer(Modifier.height(12.dp))
        }

        if (!access.notifications) {
            Text(
                "카드사 앱이 띄우는 결제 알림까지 읽으려면 알림 접근 권한이 필요하다. " +
                    "설정 화면이 열리면 목록에서 볼프를 켜고 돌아오면 된다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("알림 접근 설정 열기") }
        }
    }
}
