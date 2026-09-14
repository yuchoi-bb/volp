package com.volp.travelbudget.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PurchaseTextParserTest {

    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun `쿠팡 주문 문자에서 금액과 도착 예정일을 읽는다`() {
        val text = """
            [Web발신]
            [쿠팡] 주문이 완료되었습니다.
            상품명 : 샘소나이트 캐리어 28인치
            결제금액 : 189,000원
            도착 예정 : 9/18
        """.trimIndent()

        val parsed = PurchaseTextParser.parse(text, today)

        assertEquals(189_000L, parsed.amountKrw)
        assertEquals(LocalDate.of(2026, 9, 18), parsed.eta)
        assertEquals("쿠팡", parsed.merchant)
        assertEquals(PurchaseKind.GEAR, parsed.kind)
        assertEquals("샘소나이트 캐리어 28인치", parsed.title)
        assertEquals(PurchaseStatus.ORDERED, parsed.status)
        assertTrue(parsed.isUsable)
    }

    @Test
    fun `해가 바뀌는 배송 예정일은 다음 해로 넘긴다`() {
        val text = "[11번가] 결제금액 42,000원 배송예정일 1/5"

        val parsed = PurchaseTextParser.parse(text, LocalDate.of(2026, 12, 28))

        assertEquals(LocalDate.of(2027, 1, 5), parsed.eta)
    }

    @Test
    fun `항공권 예약 문자는 항공권으로 본다`() {
        val text = """
            대한항공 e-티켓이 발행되었습니다.
            여정 : 인천(ICN) - 간사이(KIX)
            예약번호 : ABC1234
            총 결제금액 1,240,500원
            출발일 2026년 10월 3일
        """.trimIndent()

        val parsed = PurchaseTextParser.parse(text, today)

        assertEquals(PurchaseKind.FLIGHT, parsed.kind)
        assertEquals("대한항공", parsed.merchant)
        assertEquals(1_240_500L, parsed.amountKrw)
        assertEquals("ABC1234", parsed.orderNumber)
        assertEquals("인천(ICN) - 간사이(KIX)", parsed.title)
    }

    @Test
    fun `송장번호와 택배사를 읽고 배송 중으로 본다`() {
        val text = """
            [CJ대한통운] 고객님의 상품이 출발하였습니다.
            송장번호 : 123456789012
            도착예정 2026-09-16
        """.trimIndent()

        val parsed = PurchaseTextParser.parse(text, today)

        assertEquals("123456789012", parsed.trackingNumber)
        assertEquals("CJ대한통운", parsed.carrier)
        assertEquals(PurchaseStatus.SHIPPED, parsed.status)
        assertEquals(LocalDate.of(2026, 9, 16), parsed.eta)
    }

    @Test
    fun `외화 결제는 통화와 금액을 그대로 남긴다`() {
        val text = "Agoda booking confirmed. Total USD 320.50 check-in 2026-10-04"

        val parsed = PurchaseTextParser.parse(text, today)

        assertEquals("USD", parsed.currencyCode)
        assertEquals(320.50, parsed.originalAmount!!, 0.001)
        assertEquals(0L, parsed.amountKrw)
        assertEquals(PurchaseKind.LODGING, parsed.kind)
    }

    @Test
    fun `취소 문자는 취소 상태로 읽는다`() {
        val parsed = PurchaseTextParser.parse("[G마켓] 주문이 취소되었습니다. 결제금액 55,000원", today)

        assertEquals(PurchaseStatus.CANCELLED, parsed.status)
        assertEquals("G마켓", parsed.merchant)
    }

    @Test
    fun `날짜도 금액도 없는 글은 원문만 남긴다`() {
        val parsed = PurchaseTextParser.parse("면세점에서 살 것 정리", today)

        assertEquals(0L, parsed.amountKrw)
        assertEquals(null, parsed.eta)
        assertEquals("면세점에서 살 것 정리", parsed.sourceText)
        assertTrue(parsed.title.isNotBlank())
    }

    @Test
    fun `빈 글도 터지지 않는다`() {
        val parsed = PurchaseTextParser.parse("   ", today)

        assertEquals("", parsed.title)
        assertEquals(PurchaseKind.OTHER, parsed.kind)
    }
}

class PurchaseTest {

    private val today = LocalDate.of(2026, 9, 14)

    private fun purchase(
        eta: LocalDate? = null,
        status: PurchaseStatus = PurchaseStatus.ORDERED,
        amount: Long = 10_000L,
    ) = Purchase(title = "캐리어", eta = eta, status = status, amountKrw = amount)

    @Test
    fun `출발일까지 못 오면 위험으로 본다`() {
        val tripStart = LocalDate.of(2026, 9, 20)

        assertTrue(purchase(eta = LocalDate.of(2026, 9, 21)).arrivesLate(tripStart))
        assertTrue(purchase(eta = tripStart).arrivesLate(tripStart))
        assertTrue(!purchase(eta = LocalDate.of(2026, 9, 19)).arrivesLate(tripStart))
        assertTrue(!purchase(eta = LocalDate.of(2026, 9, 21), status = PurchaseStatus.ARRIVED).arrivesLate(tripStart))
    }

    @Test
    fun `예정일이 지났는데 안 왔으면 늦은 것이다`() {
        assertTrue(purchase(eta = LocalDate.of(2026, 9, 13)).isOverdue(today))
        assertTrue(!purchase(eta = LocalDate.of(2026, 9, 14)).isOverdue(today))
        assertTrue(!purchase(eta = null).isOverdue(today))
    }

    @Test
    fun `급한 것부터 늘어놓는다`() {
        val late = purchase(eta = LocalDate.of(2026, 9, 30))
        val soon = purchase(eta = LocalDate.of(2026, 9, 16))
        val unknown = purchase(eta = null)
        val done = purchase(eta = LocalDate.of(2026, 9, 15), status = PurchaseStatus.ARRIVED)

        val sorted = Purchases.sortByUrgency(listOf(done, unknown, late, soon))

        assertEquals(listOf(soon, late, unknown, done), sorted)
    }

    @Test
    fun `아직 안 온 것들의 합만 센다`() {
        val items = listOf(
            purchase(amount = 10_000L),
            purchase(amount = 20_000L, status = PurchaseStatus.SHIPPED),
            purchase(amount = 50_000L, status = PurchaseStatus.ARRIVED),
            purchase(amount = 70_000L, status = PurchaseStatus.CANCELLED),
        )

        assertEquals(30_000L, Purchases.openTotalKrw(items))
    }
}
