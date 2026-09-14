package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 이미 보낸 예산 알림. 같은 알림을 두 번 울리지 않으려고 남긴다.
 *
 * @param alertKey 하루 단위 알림은 날짜를 포함해 하루에 한 번만 울린다.
 */
@Entity(tableName = "budget_alerts", primaryKeys = ["tripId", "alertKey"])
data class BudgetAlertEntity(
    val tripId: Long,
    val alertKey: String,
    val notifiedAt: Long,
)

@Dao
interface BudgetAlertDao {

    @Query("SELECT COUNT(*) FROM budget_alerts WHERE tripId = :tripId AND alertKey = :alertKey")
    suspend fun countSent(tripId: Long, alertKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSent(alert: BudgetAlertEntity)
}
