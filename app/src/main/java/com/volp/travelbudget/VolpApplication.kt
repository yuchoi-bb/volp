package com.volp.travelbudget

import android.app.Application
import com.volp.travelbudget.data.local.VolpDatabase
import com.volp.travelbudget.data.repository.TripRepository

/**
 * 앱 전체에서 하나만 쓰는 의존성을 들고 있는다. 규모가 작아 DI 라이브러리 대신 직접 조립한다.
 */
class VolpApplication : Application() {

    val repository: TripRepository by lazy {
        val database = VolpDatabase.get(this)
        TripRepository(database.tripDao(), database.expenseDao())
    }
}
