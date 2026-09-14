package com.volp.travelbudget.domain.cardsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 실제로 받은 카드 문자를 그대로 넣어 규칙이 깨지지 않는지 확인한다. */
class CardMessageParserTest {

    private val received = LocalDateTime.of(2026, 5, 9, 13, 35)

    private fun parse(body: String, at: LocalDateTime = received, sender: String? = null) =
        CardMessageParser.parse(body, at, sender)

    @Test
    fun `삼성카드 국내 승인을 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            삼성7414승인 최*업
            50,000원 일시불
            05/09 13:35 에이치디현대오일
            누적6,865,947원
            """.trimIndent(),
            sender = "15888900",
        )!!

        assertEquals(CardIssuer.SAMSUNG, tx.issuer)
        assertEquals("7414", tx.cardLabel)
        assertEquals("최*업", tx.holderName)
        assertEquals(TransactionKind.APPROVAL, tx.kind)
        assertEquals(50_000.0, tx.amount, 0.001)
        assertEquals("KRW", tx.currencyCode)
        assertEquals("에이치디현대오일", tx.merchant)
        assertEquals("일시불", tx.paymentPlan)
        assertEquals(6_865_947L, tx.accumulatedKrw)
        assertEquals(LocalDateTime.of(2026, 5, 9, 13, 35), tx.occurredAt)
        assertFalse(tx.isOverseas)
    }

    @Test
    fun `삼성카드 자동결제 접수를 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            [삼성카드]7414 자동결제 6/12 접수 최*리
            한율초등학교/방과후수강 166,480원
            """.trimIndent(),
            at = LocalDateTime.of(2026, 6, 15, 10, 3),
        )!!

        assertEquals(TransactionKind.APPROVAL, tx.kind)
        assertEquals(166_480.0, tx.amount, 0.001)
        assertEquals("한율초등학교/방과후수강", tx.merchant)
        assertEquals("최*리", tx.holderName)
        assertEquals("자동결제", tx.paymentPlan)
        assertEquals(LocalDateTime.of(2026, 6, 12, 0, 0), tx.occurredAt)
    }

    @Test
    fun `삼성카드 자동결제 취소는 음수 금액으로 반영한다`() {
        val tx = parse(
            """
            [Web발신]
            [삼성카드]7414 자동결제 7/10 취소 최*리
            한율초등학교/방과후수강 -101,850원
            """.trimIndent(),
            at = LocalDateTime.of(2026, 7, 13, 10, 2),
        )!!

        assertEquals(TransactionKind.CANCEL, tx.kind)
        assertEquals(101_850.0, tx.amount, 0.001)
        assertEquals(-101_850.0, tx.signedAmount, 0.001)
    }

    @Test
    fun `삼성카드 해외 승인은 현지 통화로 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            삼성7414해외승인 최*업
            JPY 1,200
            04/19 10:43 OSAKAMUSEUMOFHISTORYAI
            """.trimIndent(),
            at = LocalDateTime.of(2026, 4, 19, 10, 43),
            sender = "0220008100",
        )!!

        assertEquals(TransactionKind.APPROVAL, tx.kind)
        assertEquals("JPY", tx.currencyCode)
        assertEquals(1_200.0, tx.amount, 0.001)
        assertEquals("OSAKAMUSEUMOFHISTORYAI", tx.merchant)
        assertTrue(tx.isOverseas)
    }

    @Test
    fun `삼성카드 해외 취소를 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            삼성7414해외취소 최*업
            JPY -1,290
            04/19 14:06 DIDIMOBILITYJAPANCOR
            """.trimIndent(),
            at = LocalDateTime.of(2026, 4, 19, 14, 8),
        )!!

        assertEquals(TransactionKind.CANCEL, tx.kind)
        assertEquals(1_290.0, tx.amount, 0.001)
        assertEquals(-1_290.0, tx.signedAmount, 0.001)
    }

    @Test
    fun `삼성카드 승인거절은 지출로 기록하지 않는다`() {
        val tx = parse(
            """
            [Web발신]
            [삼성카드]해외7414 04/19 12:17
            SAKAE-KOTSU JPY 2,100 승인거절
            """.trimIndent(),
            at = LocalDateTime.of(2026, 4, 19, 12, 17),
        )!!

        assertEquals(TransactionKind.DECLINED, tx.kind)
        assertFalse(tx.isRecordable)
        assertEquals("SAKAE-KOTSU", tx.merchant)
        assertEquals(2_100.0, tx.amount, 0.001)
    }

    @Test
    fun `소수점이 있는 해외 통화를 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            삼성7414해외승인 최*업
            USD 220.90
            08/01 11:19 KOREANAIRINTERNET
            """.trimIndent(),
            at = LocalDateTime.of(2026, 8, 1, 11, 20),
        )!!

        assertEquals("USD", tx.currencyCode)
        assertEquals(220.90, tx.amount, 0.001)
    }

    @Test
    fun `신한카드 국내 승인을 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            신한카드(0088)승인 최*업
            5,900원(일시불)04/20 09:09 스타벅스코리
            누적456,975원
            """.trimIndent(),
            at = LocalDateTime.of(2026, 4, 20, 9, 9),
            sender = "15447200",
        )!!

        assertEquals(CardIssuer.SHINHAN, tx.issuer)
        assertEquals("0088", tx.cardLabel)
        assertEquals(TransactionKind.APPROVAL, tx.kind)
        assertEquals(5_900.0, tx.amount, 0.001)
        assertEquals("일시불", tx.paymentPlan)
        assertEquals("스타벅스코리", tx.merchant)
        assertEquals(456_975L, tx.accumulatedKrw)
        assertEquals(LocalDateTime.of(2026, 4, 20, 9, 9), tx.occurredAt)
    }

    @Test
    fun `신한카드 매출취소는 시각이 없어도 읽는다`() {
        val tx = parse(
            """
            [Web발신]
            신한카드 (0088) 매출취소 최*업
            3,980원  04/11 (주)오아시스
            """.trimIndent(),
            at = LocalDateTime.of(2026, 4, 14, 10, 47),
            sender = "15447000",
        )!!

        assertEquals(TransactionKind.CANCEL, tx.kind)
        assertEquals(3_980.0, tx.amount, 0.001)
        assertEquals("(주)오아시스", tx.merchant)
        assertEquals(LocalDateTime.of(2026, 4, 11, 0, 0), tx.occurredAt)
    }

    @Test
    fun `신한카드 해외 승인은 통화가 다음 줄에 와도 읽는다`() {
        val tx = parse(
            "신한(0088)해외승인 최*업 2,390\n엔      (JP)04/19 14:08 DiDi Mobil\n누적451,075원",
            at = LocalDateTime.of(2026, 4, 19, 14, 8),
            sender = "15447200",
        )!!

        assertEquals("JPY", tx.currencyCode)
        assertEquals("JP", tx.countryCode)
        assertEquals(2_390.0, tx.amount, 0.001)
        assertEquals("DiDi Mobil", tx.merchant)
        assertEquals(451_075L, tx.accumulatedKrw)
    }

    @Test
    fun `신한카드 해외 승인에 붙은 회신 머리말을 무시한다`() {
        val tx = parse(
            "RE:신한(0088)해외승인 최*업 1,980 엔\n(JP)04/18 16:26 NAMBAPARKS\n누적427,351원",
            at = LocalDateTime.of(2026, 4, 18, 16, 26),
        )!!

        assertEquals("JPY", tx.currencyCode)
        assertEquals(1_980.0, tx.amount, 0.001)
        assertEquals("NAMBAPARKS", tx.merchant)
    }

    @Test
    fun `하나카드 표 형식을 읽는다`() {
        val tx = parse(
            """
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
            """.trimIndent(),
            at = LocalDateTime.of(2026, 8, 20, 15, 5),
            sender = "18001111",
        )!!

        assertEquals(CardIssuer.HANA, tx.issuer)
        assertEquals("MG+ 하나7*4*", tx.cardLabel)
        assertEquals("최*업", tx.holderName)
        assertEquals(TransactionKind.APPROVAL, tx.kind)
        assertEquals(30_000.0, tx.amount, 0.001)
        assertEquals("구글페이먼트코리아", tx.merchant)
        assertEquals("일시불", tx.paymentPlan)
        assertEquals(126_155L, tx.accumulatedKrw)
        assertEquals(LocalDateTime.of(2026, 8, 20, 15, 5), tx.occurredAt)
    }

    @Test
    fun `연말에 받은 지난해 결제는 연도를 한 해 앞으로 본다`() {
        val tx = parse(
            """
            삼성7414승인 최*업
            10,000원 일시불
            12/30 13:35 테스트가맹점
            """.trimIndent(),
            at = LocalDateTime.of(2026, 1, 2, 10, 0),
        )!!

        assertEquals(LocalDateTime.of(2025, 12, 30, 13, 35), tx.occurredAt)
    }

    @Test
    fun `결제 문자가 아니면 읽지 않는다`() {
        assertNull(parse("[신한카드] 이번 달 혜택 안내입니다. 자세히 보기 http://example.com"))
        assertNull(parse(""))
    }
}
