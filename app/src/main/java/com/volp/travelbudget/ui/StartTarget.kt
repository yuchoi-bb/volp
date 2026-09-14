package com.volp.travelbudget.ui

/** 앱을 열자마자 보여 줄 화면. 알림이나 홈 화면 바로가기로 들어올 때 쓴다. */
sealed interface StartTarget {
    data object None : StartTarget
    data object Inbox : StartTarget
    data object QuickEntry : StartTarget
    data class Trip(val tripId: Long) : StartTarget
}
