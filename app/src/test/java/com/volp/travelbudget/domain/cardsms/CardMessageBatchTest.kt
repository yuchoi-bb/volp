package com.volp.travelbudget.domain.cardsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 문자 앱에서 여러 건을 골라 공유했을 때를 흉내 낸다. */
class CardMessageBatchTest {

    private val receivedAt = LocalDateTime.of(2026, 5, 9, 20, 0)

    private val samsung = """
        [Web발신]
        삼성7414승인 최*업
        50,000원 일시불
        05/09 13:35 에이치디현대오일
        누적6,865,947원
    """.trimIndent()

    private val shinhan = """
        [Web발신]
        신한카드(0088)승인 최*업
        5,900원(일시불)04/20 09:09 스타벅스코리
        누적456,975원
    """.trimIndent()

    private val hana = """
        [Web발신]
        승인
        금액      30,000원
        카드      MG+ 하나7*4*
        손님명    최*업
        거래종류  신용
        거래구분  일시불
        사용처    구글페이먼트코리아
        거래시간  08/20 15:05
        누적금액  126,155원
    """.trimIndent()

    @Test
    fun `한 건만 있으면 통째로 둔다`() {
        assertEquals(1, CardMessageBatch.split(samsung).size)
        assertEquals(1, CardMessageBatch.split(hana).size)
    }

    @Test
    fun `빈 줄로 나뉜 여러 건을 갈라 읽는다`() {
        val parsed = CardMessageBatch.parseAll("$samsung\n\n$shinhan", receivedAt)

        assertEquals(2, parsed.size)
        assertEquals(CardIssuer.SAMSUNG, parsed[0].issuer)
        assertEquals("에이치디현대오일", parsed[0].merchant)
        assertEquals(CardIssuer.SHINHAN, parsed[1].issuer)
        assertEquals("스타벅스코리", parsed[1].merchant)
    }

    @Test
    fun `표 형식이 섞여도 갈라 읽는다`() {
        val parsed = CardMessageBatch.parseAll("$samsung\n\n$hana", receivedAt)

        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.issuer == CardIssuer.HANA })
    }

    @Test
    fun `머리말 없이 카드사 이름으로 시작해도 가른다`() {
        val text = """
            삼성7414승인 최*업
            50,000원 일시불
            05/09 13:35 에이치디현대오일
            누적6,865,947원
            신한카드(0088)승인 최*업
            5,900원(일시불)04/20 09:09 스타벅스코리
            누적456,975원
        """.trimIndent()

        assertEquals(2, CardMessageBatch.split(text).size)
    }

    @Test
    fun `가맹점 이름이 카드사로 시작해도 자르지 않는다`() {
        val text = """
            [Web발신]
            삼성7414승인 최*업
            12,000원 일시불
            05/09 13:35 하나로마트양재
            누적6,865,947원
        """.trimIndent()

        val parsed = CardMessageBatch.parseAll(text, receivedAt)

        assertEquals(1, parsed.size)
        assertEquals("하나로마트양재", parsed.first().merchant)
    }

    @Test
    fun `읽히지 않는 조각은 버린다`() {
        val junk = """
            [Web발신]
            택배가 도착했습니다. 문 앞에 두었습니다.
        """.trimIndent()

        val parsed = CardMessageBatch.parseAll("$junk\n\n$samsung", receivedAt)

        assertEquals(1, parsed.size)
        assertEquals("에이치디현대오일", parsed.first().merchant)
    }

    @Test
    fun `같은 결제가 두 번 들어 있으면 한 건만 남긴다`() {
        val parsed = CardMessageBatch.parseAll("$samsung\n\n$samsung", receivedAt)

        assertEquals(1, parsed.size)
    }

    @Test
    fun `빈 글은 아무것도 아니다`() {
        assertEquals(emptyList<String>(), CardMessageBatch.split("   "))
        assertEquals(emptyList<CardTransaction>(), CardMessageBatch.parseAll("", receivedAt))
    }
}
