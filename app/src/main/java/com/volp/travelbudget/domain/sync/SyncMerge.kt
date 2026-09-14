package com.volp.travelbudget.domain.sync

/**
 * 기기가 달라도 같은 기록임을 알아보게 하는 값.
 *
 * 로컬 데이터베이스의 행 번호는 기기마다 다르게 매겨져 두 대를 맞출 때 쓸 수 없다.
 * 그래서 기록을 만들 때 기기와 무관한 [uid]를 붙이고, 고칠 때마다 [updatedAt]을 올린다.
 */
interface Syncable {
    val uid: String
    val updatedAt: Long
}

/**
 * 지운 기록의 흔적.
 *
 * 지움을 남기지 않으면 다른 기기에 남아 있던 기록이 다음 동기화에서 되살아난다.
 */
data class Tombstone(
    val uid: String,
    val deletedAt: Long,
)

data class MergeOutcome<T : Syncable>(
    /** 이 기기에 새로 넣거나 덮어써야 할 것. */
    val incoming: List<T>,
    /** 이 기기에서 지워야 할 것. */
    val removedUids: List<String>,
    /** 두 기기를 합친 결과. 이대로 올린다. */
    val merged: List<T>,
    val tombstones: List<Tombstone>,
)

/**
 * 두 기기의 기록을 합친다.
 *
 * 한쪽으로 덮어쓰면 다른 폰에서 넣은 것이 통째로 사라진다. 그래서 기록 하나하나를 [Syncable.uid]로
 * 짝지어 보고, 같은 기록이 양쪽에서 고쳐졌으면 **나중에 고친 쪽**을 남긴다. 같은 기록을 두 폰에서
 * 동시에 고치는 일은 드물고, 그때 한쪽을 고르는 것이 둘 다 잃는 것보다 낫다.
 */
object SyncMerge {

    fun <T : Syncable> merge(
        local: List<T>,
        remote: List<T>,
        localTombstones: List<Tombstone> = emptyList(),
        remoteTombstones: List<Tombstone> = emptyList(),
    ): MergeOutcome<T> {
        val tombstones = mergeTombstones(localTombstones, remoteTombstones)
        val deletedAt = tombstones.associate { it.uid to it.deletedAt }

        val localByUid = local.associateBy { it.uid }
        val remoteByUid = remote.associateBy { it.uid }

        val incoming = mutableListOf<T>()
        val removed = mutableListOf<String>()
        val merged = mutableListOf<T>()

        (localByUid.keys + remoteByUid.keys).forEach { uid ->
            val mine = localByUid[uid]
            val theirs = remoteByUid[uid]
            val winner = pick(mine, theirs) ?: return@forEach

            val deleted = deletedAt[uid]
            if (deleted != null && deleted >= winner.updatedAt) {
                // 지운 뒤로 고친 적이 없으면 지운 것이 맞다.
                if (mine != null) removed += uid
                return@forEach
            }

            merged += winner
            // 저쪽 것이 이겼을 때만 이 기기에 반영하면 된다.
            if (winner === theirs && (mine == null || theirs.updatedAt > mine.updatedAt)) {
                incoming += theirs
            }
        }

        return MergeOutcome(
            incoming = incoming,
            removedUids = removed,
            merged = merged,
            tombstones = tombstones,
        )
    }

    /** 같은 값이면 이 기기 것을 남긴다. 둘 다 맞으니 굳이 다시 쓸 이유가 없다. */
    private fun <T : Syncable> pick(mine: T?, theirs: T?): T? = when {
        mine == null -> theirs
        theirs == null -> mine
        theirs.updatedAt > mine.updatedAt -> theirs
        else -> mine
    }

    private fun mergeTombstones(
        local: List<Tombstone>,
        remote: List<Tombstone>,
    ): List<Tombstone> = (local + remote)
        .groupBy { it.uid }
        .map { (uid, entries) -> Tombstone(uid, entries.maxOf { it.deletedAt }) }
}
