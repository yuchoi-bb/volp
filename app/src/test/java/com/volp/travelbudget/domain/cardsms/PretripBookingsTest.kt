package com.volp.travelbudget.domain.cardsms

import com.volp.travelbudget.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PretripBookingsTest {

    @Test
    fun `여행 시작 전날까지만 훑는다`() {
        val window = PretripBookings.window(LocalDate.of(2026, 5, 10), 30)

        assertEquals(LocalDate.of(2026, 4, 10), window.start)
        assertEquals(LocalDate.of(2026, 5, 9), window.endInclusive)
    }

    @Test
    fun `항공권 결제를 항공으로 읽는다`() {
        assertEquals(PretripKind.FLIGHT, PretripBookings.kindOf("대한항공"))
        assertEquals(PretripKind.FLIGHT, PretripBookings.kindOf("(주)제주항공"))
        assertEquals(PretripKind.FLIGHT, PretripBookings.kindOf("KOREAN AIR"))
        assertEquals(PretripKind.FLIGHT, PretripBookings.kindOf("에어부산"))
    }

    @Test
    fun `숙소 결제를 숙박으로 읽는다`() {
        assertEquals(PretripKind.LODGING, PretripBookings.kindOf("AGODA"))
        assertEquals(PretripKind.LODGING, PretripBookings.kindOf("호텔신라"))
        assertEquals(PretripKind.LODGING, PretripBookings.kindOf("야놀자"))
    }

    @Test
    fun `에어비앤비는 항공이 아니라 숙소다`() {
        assertEquals(PretripKind.LODGING, PretripBookings.kindOf("AIRBNB"))
    }

    @Test
    fun `렌터카 결제를 현지 교통으로 읽는다`() {
        assertEquals(PretripKind.RENTAL, PretripBookings.kindOf("롯데렌터카"))
        assertEquals(PretripKind.RENTAL, PretripBookings.kindOf("HERTZ RENT A CAR"))
        assertEquals(ExpenseCategory.TRANSPORT, PretripKind.RENTAL.category)
    }

    @Test
    fun `여행사 결제를 예약으로 읽는다`() {
        assertEquals(PretripKind.AGENCY, PretripBookings.kindOf("하나투어"))
        assertEquals(PretripKind.AGENCY, PretripBookings.kindOf("마이리얼트립"))
    }

    @Test
    fun `평소 결제는 걸러 낸다`() {
        assertNull(PretripBookings.kindOf("스타벅스"))
        assertNull(PretripBookings.kindOf("이마트"))
        assertNull(PretripBookings.kindOf("배달의민족"))
        // 미용실이 항공으로 딸려 오지 않는다.
        assertNull(PretripBookings.kindOf("BLUE HAIR SHOP"))
        assertNull(PretripBookings.kindOf("GS25 역삼점"))
    }
}
