package com.volp.travelbudget.domain.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BookingTextParserTest {

    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun `e-티켓에서 편명과 노선과 출발 시각을 읽는다`() {
        val text = """
            [대한항공] e-티켓이 발행되었습니다.
            편명 : KE723
            여정 : 인천(ICN) - 간사이(KIX)
            출발 2026년 10월 3일 09:05
            도착 2026년 10월 3일 11:00
            예약번호 ABC1234
            좌석 : 32A
        """.trimIndent()

        val parsed = BookingTextParser.parse(text, today)

        assertEquals(BookingType.FLIGHT, parsed.type)
        assertEquals("KE723", parsed.title)
        assertEquals("대한항공", parsed.provider)
        assertEquals("ICN", parsed.fromCode)
        assertEquals("KIX", parsed.toCode)
        assertEquals("인천", parsed.fromName)
        assertEquals(LocalDate.of(2026, 10, 3), parsed.startDate)
        assertEquals(LocalTime.of(9, 5), parsed.startTime)
        assertEquals(LocalTime.of(11, 0), parsed.endTime)
        assertEquals("ABC1234", parsed.confirmationCode)
        assertEquals("32A", parsed.seat)
    }

    @Test
    fun `숙소 확인 문자는 체크인과 체크아웃을 읽는다`() {
        val text = """
            아고다 예약이 확정되었습니다.
            도미인 난바 호텔
            체크인 2026년 10월 3일 15:00
            체크아웃 2026년 10월 6일 11:00
            예약번호 : 987654321
            주소 : 오사카시 주오구 니혼바시 1-2-3
        """.trimIndent()

        val parsed = BookingTextParser.parse(text, today)

        assertEquals(BookingType.LODGING, parsed.type)
        assertEquals("아고다", parsed.provider)
        assertEquals(LocalDate.of(2026, 10, 3), parsed.startDate)
        assertEquals(LocalTime.of(15, 0), parsed.startTime)
        assertEquals(LocalDate.of(2026, 10, 6), parsed.endDate)
        assertEquals(LocalTime.of(11, 0), parsed.endTime)
        assertTrue(parsed.title.contains("도미인"))
        assertTrue(parsed.address.contains("오사카"))
    }

    @Test
    fun `화살표 노선만 있어도 읽는다`() {
        val parsed = BookingTextParser.parse("항공편 ICN → NRT 10/3 출발 08:20", today)

        assertEquals("ICN", parsed.fromCode)
        assertEquals("NRT", parsed.toCode)
        assertEquals(LocalDate.of(2026, 10, 3), parsed.startDate)
    }

    @Test
    fun `오후 시각도 읽는다`() {
        val parsed = BookingTextParser.parse("KTX 승차권 10월 3일 오후 2시 30분 출발", today)

        assertEquals(BookingType.TRAIN, parsed.type)
        assertEquals(LocalTime.of(14, 30), parsed.startTime)
    }

    @Test
    fun `항공사 코드가 아닌 것을 편명으로 읽지 않는다`() {
        val parsed = BookingTextParser.parse("탑승 안내 XX999 10/3 09:00 출발", today)

        assertTrue("편명으로 읽으면 안 된다: ${parsed.title}", parsed.title != "XX999")
    }

    @Test
    fun `도착이 출발보다 앞서면 버린다`() {
        val text = "출발 2026년 10월 5일 09:00 도착 2026년 10월 1일 11:00"

        val parsed = BookingTextParser.parse(text, today)

        assertEquals(LocalDate.of(2026, 10, 5), parsed.startDate)
        assertNull(parsed.endDate)
    }

    @Test
    fun `시각이 없으면 기본 시각을 붙여 준다`() {
        val parsed = BookingTextParser.parse("항공권 예약 완료 10월 3일 인천 출발", today)

        assertEquals(LocalDate.of(2026, 10, 3), parsed.startDate)
        assertNull(parsed.startTime)
        assertEquals(LocalTime.of(9, 0), parsed.startAt()!!.toLocalTime())
    }

    @Test
    fun `예약으로 읽히지 않는 글은 확신이 낮다`() {
        val order = BookingTextParser.parse("[쿠팡] 캐리어 결제금액 189,000원 도착 예정 9/18", today)
        val ticket = BookingTextParser.parse(
            "대한항공 KE723 인천(ICN) - 간사이(KIX) 출발 2026년 10월 3일 09:05 예약번호 ABC1234",
            today,
        )

        assertTrue("예약 쪽이 더 확신이 높아야 한다", ticket.confidence > order.confidence)
    }

    @Test
    fun `빈 글도 터지지 않는다`() {
        val parsed = BookingTextParser.parse("   ", today)

        assertEquals(BookingType.OTHER, parsed.type)
        assertNull(parsed.startDate)
    }
}
