package com.volp.travelbudget.domain.cardsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportGroupingTest {

    @Test
    fun `해외 여행 중의 현지 통화 결제는 여행 중 결제다`() {
        val group = ImportGrouping.groupFor("JPY", isOverseas = true, pretrip = false)

        assertEquals(ImportGroup.ONSITE, group)
    }

    @Test
    fun `해외 여행 중의 원화 결제는 고민되는 결제로 뺀다`() {
        val group = ImportGrouping.groupFor("JPY", isOverseas = false, pretrip = false)

        assertEquals(ImportGroup.UNCERTAIN, group)
    }

    @Test
    fun `국내 여행은 모두 원화라 가릴 것이 없다`() {
        val group = ImportGrouping.groupFor("KRW", isOverseas = false, pretrip = false)

        assertEquals(ImportGroup.ONSITE, group)
    }

    @Test
    fun `여행 전 예약은 통화와 상관없이 제 묶음으로 간다`() {
        assertEquals(
            ImportGroup.PRETRIP,
            ImportGrouping.groupFor("JPY", isOverseas = false, pretrip = true),
        )
        assertEquals(
            ImportGroup.PRETRIP,
            ImportGrouping.groupFor("KRW", isOverseas = true, pretrip = true),
        )
    }

    @Test
    fun `여행사와 투어는 여행 것으로 읽는다`() {
        assertTrue(ImportGrouping.looksLikeTravel("하나투어"))
        assertTrue(ImportGrouping.looksLikeTravel("클룩"))
        assertFalse(ImportGrouping.looksLikeTravel("통신요금 자동이체"))
    }

    @Test
    fun `고민되는 묶음은 여행으로 읽히는 것만 켜 둔다`() {
        assertTrue(
            ImportGrouping.checkedByDefault(ImportGroup.UNCERTAIN, alreadyThere = false, looksLikeTravel = true),
        )
        assertFalse(
            ImportGrouping.checkedByDefault(ImportGroup.UNCERTAIN, alreadyThere = false, looksLikeTravel = false),
        )
    }

    @Test
    fun `나머지 묶음은 켜 두되 이미 있는 것은 켜지 않는다`() {
        assertTrue(
            ImportGrouping.checkedByDefault(ImportGroup.ONSITE, alreadyThere = false, looksLikeTravel = false),
        )
        assertFalse(
            ImportGrouping.checkedByDefault(ImportGroup.ONSITE, alreadyThere = true, looksLikeTravel = true),
        )
        assertFalse(
            ImportGrouping.checkedByDefault(ImportGroup.PRETRIP, alreadyThere = true, looksLikeTravel = true),
        )
    }
}
