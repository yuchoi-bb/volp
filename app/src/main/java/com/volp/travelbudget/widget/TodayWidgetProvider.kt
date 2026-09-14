package com.volp.travelbudget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.volp.travelbudget.MainActivity
import com.volp.travelbudget.R
import com.volp.travelbudget.VolpApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 홈 화면에 붙는 두 줄짜리 위젯.
 *
 * 여행 중에 가장 자주 궁금한 것은 오늘 더 쓸 수 있는 돈과 다음에 갈 곳이다. 그 둘을 보려고
 * 앱을 열고 탭을 고르는 것은 번거롭다. 눌렀을 때는 그 여행의 오늘 화면으로 바로 들어간다.
 *
 * 위젯은 Compose가 아니라 [RemoteViews]로 그린다. 시스템이 다른 프로세스에서 그리기 때문이다.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val application = context.applicationContext as? VolpApplication ?: return
        // 데이터베이스를 읽어야 해서 곧바로 그릴 수 없다. 먼저 지난 값을 남겨 두고 채워 넣는다.
        val pending = goAsync()

        scope.launch {
            val data = runCatching {
                TodayWidget.load(
                    repository = application.repository,
                    stopsOf = { tripId -> application.itineraryRepository.stopsOnce(tripId) },
                    bookingsOf = { tripId -> application.bookingRepository.bookingsOnce(tripId) },
                )
            }.getOrDefault(TodayWidgetData.IDLE)

            ids.forEach { id -> manager.updateAppWidget(id, render(context, data)) }
            pending.finish()
        }
    }

    private fun render(context: Context, data: TodayWidgetData): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_title, data.title)
            setTextViewText(R.id.widget_money, data.money)
            setTextViewText(R.id.widget_next, data.next)
            setOnClickPendingIntent(R.id.widget_root, openIntent(context, data.tripId))
        }

    private fun openIntent(context: Context, tripId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (tripId != null) putExtra(MainActivity.EXTRA_OPEN_TRIP_ID, tripId)
        }
        return PendingIntent.getActivity(
            context,
            (tripId ?: 0L).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * 기록이 바뀌었을 때 위젯을 다시 그리게 한다.
         *
         * 위젯이 스스로 도는 주기는 삼십 분이 최소라, 지출을 넣자마자 숫자가 그대로면
         * 사용자는 기록이 안 들어갔다고 여긴다.
         */
        fun notifyChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, TodayWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
