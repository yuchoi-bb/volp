package com.volp.travelbudget.domain.cardsms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 전용 규칙이 없는 카드사 문자.
 *
 * 서식은 카드사마다 다르지만 뼈대는 같다. 뼈대만 보고 읽는 규칙이 제대로 도는지 본다.
 */
class GenericCardRulesTest {

    private val receivedAt = LocalDateTime.of(2026, 9, 30, 14, 25)

    private fun parse(body: String) = CardMessageParser.parse(body, receivedAt, sender = null)

    @Test
    fun `국민카드 국내 승인`() {
        val parsed = parse(
            """
            [Web발신]
            KB국민카드 승인 홍*동
            12,345원 일시불
            09/30 14:23
            스타벅스강남점
            """.trimIndent(),
        )

        assertNotNull(parsed)
        assertEquals(CardIssuer.KOOKMIN, parsed!!.issuer)
        assertEquals(12345.0, parsed.amount, 0.0)
        assertEquals("스타벅스강남점", parsed.merchant)
        assertEquals(LocalDateTime.of(2026, 9, 30, 14, 23), parsed.occurredAt)
        assertEquals(TransactionKind.APPROVAL, parsed.kind)
        assertEquals("일시불", parsed.paymentPlan)
        assertEquals("홍*동", parsed.holderName)
    }

    @Test
    fun `현대카드 한 줄짜리 승인`() {
        val parsed = parse("[Web발신] 현대카드(1234) 승인 홍*동 8,900원 일시불 09/29 12:05 올리브영제주점")

        assertNotNull(parsed)
        assertEquals(CardIssuer.HYUNDAI, parsed!!.issuer)
        assertEquals(8900.0, parsed.amount, 0.0)
        assertEquals("1234", parsed.cardLabel)
        assertEquals("올리브영제주점", parsed.merchant)
    }

    @Test
    fun `롯데카드 누적 금액이 붙어도 가맹점만 남는다`() {
        val parsed = parse("[Web발신] 롯데카드 승인 23,000원 일시불 09/28 19:40 제주흑돼지 누적 1,230,000원")

        assertNotNull(parsed)
        assertEquals("제주흑돼지", parsed!!.merchant)
        assertEquals(1_230_000L, parsed.accumulatedKrw)
    }

    @Test
    fun `우리카드 취소는 취소로 읽는다`() {
        val parsed = parse("[Web발신] 우리카드(5678) 매출취소 홍*동 5,000원 09/27 10:10 카페한라")

        assertNotNull(parsed)
        assertEquals(TransactionKind.CANCEL, parsed!!.kind)
        assertEquals(5000.0, parsed.amount, 0.0)
        assertEquals(-5000.0, parsed.signedAmount, 0.0)
    }

    @Test
    fun `NH농협 시각이 없어도 날짜로 읽는다`() {
        val parsed = parse("[Web발신] NH농협카드 승인 15,000원 09/26 성산일출봉매점")

        assertNotNull(parsed)
        assertEquals(CardIssuer.NONGHYUP, parsed!!.issuer)
        assertEquals(LocalDateTime.of(2026, 9, 26, 0, 0), parsed.occurredAt)
        assertEquals("성산일출봉매점", parsed.merchant)
    }

    @Test
    fun `승인거절은 기록하지 않는다`() {
        val parsed = parse("[Web발신] BC카드 승인거절 50,000원 09/30 14:20 주유소")

        assertNotNull(parsed)
        assertEquals(TransactionKind.DECLINED, parsed!!.kind)
        assertEquals(false, parsed.isRecordable)
    }

    @Test
    fun `카드사 이름이 없으면 읽지 않는다`() {
        // 금액이 적힌 문자는 널려 있다. 그것까지 지출로 올리면 미확인함이 쓸모없어진다.
        assertNull(parse("[Web발신] 주문하신 상품 12,345원이 09/30 14:23 발송되었습니다"))
    }

    @Test
    fun `승인이나 취소라는 말이 없으면 읽지 않는다`() {
        assertNull(parse("[Web발신] KB국민카드 명세서 12,345원 09/30 결제 예정"))
    }

    @Test
    fun `해외 결제는 전용 규칙에 맡긴다`() {
        assertNull(parse("[Web발신] 국민카드 해외승인 홍*동 30.00 USD 09/30 14:23 AMAZON"))
    }

    @Test
    fun `전용 규칙이 있는 카드사는 그쪽이 먼저다`() {
        val parsed = parse("신한카드(0088)승인 최*업\n5,900원(일시불)04/20 09:09 스타벅스코리\n누적456,975원")

        assertNotNull(parsed)
        assertEquals(CardIssuer.SHINHAN, parsed!!.issuer)
        assertEquals("0088", parsed.cardLabel)
    }
}
