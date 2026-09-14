package com.volp.travelbudget.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TravelDocumentTest {

    private val today = LocalDate.of(2026, 9, 14)

    private fun document(
        kind: DocumentKind = DocumentKind.PASSPORT,
        title: String = "여권",
        number: String = "",
        expiresOn: LocalDate? = null,
        tripId: Long? = null,
    ) = TravelDocument(
        kind = kind,
        title = title,
        number = number,
        expiresOn = expiresOn,
        tripId = tripId,
    )

    @Test
    fun `번호는 뒤 네 자리만 남기고 가린다`() {
        assertEquals("•••••5678", document(number = "M12345678").maskedNumber)
    }

    @Test
    fun `짧은 번호는 가리지 않는다`() {
        assertEquals("1234", document(number = "1234").maskedNumber)
        assertEquals("", document(number = "").maskedNumber)
    }

    @Test
    fun `여섯 달 안에 만료되면 챙겨야 한다`() {
        assertTrue(document(expiresOn = today.plusMonths(3)).expiresSoon(today))
        assertTrue(!document(expiresOn = today.plusYears(3)).expiresSoon(today))
        assertTrue(!document(expiresOn = null).expiresSoon(today))
    }

    @Test
    fun `이미 만료된 것은 곧 만료되는 것과 구별한다`() {
        val expired = document(expiresOn = today.minusDays(1))

        assertTrue(expired.expired(today))
        assertTrue(!expired.expiresSoon(today))
        assertEquals(-1L, expired.daysUntilExpiry(today))
    }

    @Test
    fun `챙겨야 할 것이 위로 온다`() {
        val fine = document(title = "보험", kind = DocumentKind.INSURANCE, expiresOn = today.plusYears(2))
        val soon = document(title = "여권", expiresOn = today.plusMonths(2))
        val none = document(title = "바우처", kind = DocumentKind.VOUCHER)

        val sorted = TravelDocuments.sort(listOf(fine, none, soon), today)

        assertEquals("여권", sorted.first().title)
        assertEquals(1, TravelDocuments.needsAttention(sorted, today).size)
    }

    @Test
    fun `여행에 묶이지 않은 문서는 어느 여행에서나 보인다`() {
        val passport = document(title = "여권")
        val mine = document(title = "오사카 바우처", kind = DocumentKind.VOUCHER, tripId = 1L)
        val other = document(title = "파리 바우처", kind = DocumentKind.VOUCHER, tripId = 2L)

        val forTrip = TravelDocuments.forTrip(listOf(passport, mine, other), tripId = 1L)

        assertEquals(listOf("여권", "오사카 바우처"), forTrip.map { it.title })
    }

    @Test
    fun `만료일을 묻는 것이 말이 되는 종류만 표시한다`() {
        assertTrue(DocumentKind.PASSPORT.hasExpiry)
        assertTrue(DocumentKind.INSURANCE.hasExpiry)
        assertTrue(!DocumentKind.VOUCHER.hasExpiry)
    }
}
