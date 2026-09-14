package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 가맹점 이름을 사람이 읽는 이름과 항목으로 바꿔 주는 사전.
 *
 * 해외 결제 문자의 가맹점명은 잘린 영문 대문자로 온다(`OSAKAMUSEUMOFHISTORYAI`).
 * 한 번 고쳐 두면 다음부터 같은 가맹점은 자동으로 정리된다.
 */
@Entity(tableName = "merchant_aliases")
data class MerchantAliasEntity(
    /** 문자에 찍힌 원래 이름을 정규화한 값. */
    @PrimaryKey val rawKey: String,
    val displayName: String,
    val category: String,
    val updatedAt: Long,
)

@Dao
interface MerchantAliasDao {

    @Query("SELECT * FROM merchant_aliases ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantAliasEntity>>

    @Query("SELECT * FROM merchant_aliases WHERE rawKey = :rawKey")
    suspend fun find(rawKey: String): MerchantAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: MerchantAliasEntity)

    @Query("DELETE FROM merchant_aliases WHERE rawKey = :rawKey")
    suspend fun deleteByKey(rawKey: String)
}

/** 표기 차이를 없애 같은 가맹점이 여러 줄로 쌓이지 않게 한다. */
fun normalizeMerchantKey(merchant: String): String =
    merchant.uppercase().filter { !it.isWhitespace() && it != '.' && it != '-' && it != '*' }
