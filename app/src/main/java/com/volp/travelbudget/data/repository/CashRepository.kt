package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.local.CashTopUpDao
import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.data.local.SyncDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.domain.cash.CashTopUp
import com.volp.travelbudget.domain.sync.SyncIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 환전·인출로 지갑에 들어온 현금을 맡는다. */
class CashRepository(
    private val dao: CashTopUpDao,
    private val syncDao: SyncDao,
) {

    fun observeTopUps(tripId: Long): Flow<List<CashTopUp>> =
        dao.observeByTrip(tripId).map { list -> list.map { it.toDomain() } }

    suspend fun topUpsOnce(tripId: Long): List<CashTopUp> =
        dao.findByTrip(tripId).map { it.toDomain() }

    suspend fun find(id: Long): CashTopUp? = dao.findById(id)?.toDomain()

    suspend fun save(topUp: CashTopUp): Long {
        val stamped = topUp.copy(
            uid = topUp.uid.ifBlank { SyncIds.newUid() },
            updatedAt = SyncIds.now(),
            createdAt = if (topUp.createdAt > 0L) topUp.createdAt else SyncIds.now(),
        )
        return if (stamped.id > 0L) {
            dao.update(stamped.toEntity())
            stamped.id
        } else {
            dao.insert(stamped.toEntity())
        }
    }

    suspend fun delete(id: Long) {
        dao.uidOf(id)?.let { uid ->
            syncDao.recordDeletion(DeletionEntity(ENTITY, uid, SyncIds.now()))
        }
        dao.deleteById(id)
    }

    private companion object {
        const val ENTITY = "cash"
    }
}
