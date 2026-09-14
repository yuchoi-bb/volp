package com.volp.travelbudget.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptReadingTest {

    @Test
    fun `코드 블록에 싸여 와도 JSON만 떼어 낸다`() {
        val raw = """
            ```json
            {"merchant":"이치란","total":1290,"currency":"JPY"}
            ```
        """.trimIndent()

        assertEquals("""{"merchant":"이치란","total":1290,"currency":"JPY"}""", ReceiptParsing.extractJson(raw))
    }

    @Test
    fun `JSON이 없으면 null`() {
        assertNull(ReceiptParsing.extractJson("읽을 수 없습니다"))
        assertNull(ReceiptParsing.extractJson(""))
    }

    @Test
    fun `금액에 기호가 섞여도 읽는다`() {
        assertEquals(12500.0, ReceiptParsing.parseAmount("₩12,500")!!, 0.001)
        assertEquals(32.5, ReceiptParsing.parseAmount("$32.50")!!, 0.001)
        assertNull(ReceiptParsing.parseAmount("0"))
        assertNull(ReceiptParsing.parseAmount(null))
    }

    @Test
    fun `통화는 세 글자일 때만 인정한다`() {
        assertEquals("JPY", ReceiptParsing.normalizeCurrency("jpy"))
        assertEquals("", ReceiptParsing.normalizeCurrency("엔"))
        assertEquals("", ReceiptParsing.normalizeCurrency(null))
    }

    @Test
    fun `날짜는 못 읽으면 비워 둔다`() {
        assertEquals(LocalDate.of(2026, 1, 31), ReceiptParsing.parseDate("2026-01-31"))
        assertNull(ReceiptParsing.parseDate("2026년 1월"))
        assertNull(ReceiptParsing.parseDate(""))
    }

    @Test
    fun `금액이 있어야 쓸 만한 읽기다`() {
        assertTrue(ReceiptReading(total = 1290.0).isUsable)
        assertTrue(!ReceiptReading(merchant = "이치란").isUsable)
    }

    @Test
    fun `읽은 것을 한 줄로 알려 준다`() {
        val reading = ReceiptReading(
            merchant = "이치란",
            total = 1290.0,
            currencyCode = "JPY",
            date = LocalDate.of(2026, 10, 3),
        )

        assertEquals("이치란 · 1290.0 JPY · 2026-10-03", reading.summary)
        assertEquals("읽어 낸 것이 없다", ReceiptReading().summary)
    }
}
