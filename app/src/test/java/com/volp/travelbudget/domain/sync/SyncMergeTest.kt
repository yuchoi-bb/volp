package com.volp.travelbudget.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Note(
    override val uid: String,
    override val updatedAt: Long,
    val text: String,
) : Syncable

class SyncMergeTest {

    private fun uids(notes: List<Note>) = notes.map { it.uid }.sorted()

    @Test
    fun `양쪽에만 있는 기록은 둘 다 살아남는다`() {
        val outcome = SyncMerge.merge(
            local = listOf(Note("a", 100, "내 폰")),
            remote = listOf(Note("b", 100, "다른 폰")),
        )

        assertEquals(listOf("a", "b"), uids(outcome.merged))
        assertEquals(listOf("b"), uids(outcome.incoming))
        assertTrue(outcome.removedUids.isEmpty())
    }

    @Test
    fun `같은 기록은 나중에 고친 쪽이 남는다`() {
        val outcome = SyncMerge.merge(
            local = listOf(Note("a", 100, "예전")),
            remote = listOf(Note("a", 200, "나중")),
        )

        assertEquals("나중", outcome.merged.single().text)
        assertEquals("나중", outcome.incoming.single().text)
    }

    @Test
    fun `이 기기가 더 새것이면 저쪽 것을 받아오지 않는다`() {
        val outcome = SyncMerge.merge(
            local = listOf(Note("a", 300, "내가 방금 고침")),
            remote = listOf(Note("a", 200, "저쪽 옛것")),
        )

        assertEquals("내가 방금 고침", outcome.merged.single().text)
        assertTrue(outcome.incoming.isEmpty())
    }

    @Test
    fun `시각이 같으면 이 기기 것을 그대로 둔다`() {
        val outcome = SyncMerge.merge(
            local = listOf(Note("a", 100, "이쪽")),
            remote = listOf(Note("a", 100, "저쪽")),
        )

        assertEquals("이쪽", outcome.merged.single().text)
        assertTrue(outcome.incoming.isEmpty())
    }

    @Test
    fun `다른 폰에서 지운 것은 이 폰에서도 지운다`() {
        val outcome = SyncMerge.merge(
            local = listOf(Note("a", 100, "남아 있던 것")),
            remote = emptyList(),
            remoteTombstones = listOf(Tombstone("a", 150)),
        )

        assertTrue(outcome.merged.isEmpty())
        assertEquals(listOf("a"), outcome.removedUids)
    }

    @Test
    fun `지운 뒤에 다시 고친 기록은 되살아난다`() {
        val outcome = SyncMerge.merge(
            local = listOf(Note("a", 300, "지우고 나서 다시 씀")),
            remote = emptyList(),
            remoteTombstones = listOf(Tombstone("a", 150)),
        )

        assertEquals("지우고 나서 다시 씀", outcome.merged.single().text)
        assertTrue(outcome.removedUids.isEmpty())
    }

    @Test
    fun `지운 흔적은 양쪽 것을 모두 들고 간다`() {
        val outcome = SyncMerge.merge(
            local = emptyList<Note>(),
            remote = emptyList(),
            localTombstones = listOf(Tombstone("a", 100)),
            remoteTombstones = listOf(Tombstone("a", 200), Tombstone("b", 50)),
        )

        assertEquals(listOf("a", "b"), outcome.tombstones.map { it.uid }.sorted())
        // 같은 기록이면 나중에 지운 시각을 남긴다.
        assertEquals(200, outcome.tombstones.first { it.uid == "a" }.deletedAt)
    }

    @Test
    fun `한쪽이 비어 있어도 다른 쪽 기록을 잃지 않는다`() {
        val mine = listOf(Note("a", 100, "첫 폰"), Note("b", 100, "첫 폰"))

        val outcome = SyncMerge.merge(local = mine, remote = emptyList())
        assertEquals(listOf("a", "b"), uids(outcome.merged))

        val fresh = SyncMerge.merge(local = emptyList(), remote = mine)
        assertEquals(listOf("a", "b"), uids(fresh.merged))
        assertEquals(listOf("a", "b"), uids(fresh.incoming))
    }
}
