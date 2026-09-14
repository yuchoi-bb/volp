package com.volp.travelbudget.domain.sync

import java.util.UUID

/** 기기와 무관한 기록 식별자를 만든다. */
object SyncIds {
    fun newUid(): String = UUID.randomUUID().toString()

    fun now(): Long = System.currentTimeMillis()
}
