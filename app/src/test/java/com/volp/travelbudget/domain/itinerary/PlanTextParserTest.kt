package com.volp.travelbudget.domain.itinerary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanTextParserTest {

    private val today = LocalDate.of(2026, 9, 16)
    private val tripStart = LocalDate.of(2026, 10, 1)
    private val tripEnd = LocalDate.of(2026, 10, 6)

    private val aiPlan = """
        제주 3박 4일 일정을 짜 봤습니다. 렌터카 기준이며 동선을 최소화했습니다.

        ## 1일차 (10/1, 목) — 제주 도착
        - 09:00 김포공항 출발 (대한항공 KE1231)
        - 11:00 렌터카 픽업
        - 오후: 성산일출봉 — 입장료 5,000원
        - 저녁: 흑돼지 거리

        ## 2일차 (10/2, 금)
        * 우도 투어 09:30 예매 필요
        * 카페 거리 산책
    """.trimIndent()

    @Test
    fun `AI가 짜 준 일정을 하루씩 나눈다`() {
        val days = PlanTextParser.parse(aiPlan, today)

        assertEquals(2, days.size)
        assertEquals(1, days[0].dayNumber)
        assertEquals(LocalDate.of(2026, 10, 1), days[0].date)
        assertEquals(4, days[0].stops.size)
        assertEquals(2, days[1].stops.size)
    }

    @Test
    fun `시각과 제목과 메모를 갈라 읽는다`() {
        val first = PlanTextParser.parse(aiPlan, today).first().stops.first()

        assertEquals("김포공항 출발", first.title)
        assertEquals(LocalTime.of(9, 0), first.startTime)
        assertEquals("대한항공 KE1231", first.memo)
    }

    @Test
    fun `앞머리의 오전 저녁 같은 말은 제목에서 뗀다`() {
        val stops = PlanTextParser.parse(aiPlan, today).first().stops

        assertEquals("성산일출봉", stops[2].title)
        assertEquals("입장료 5,000원", stops[2].memo)
        assertEquals("흑돼지 거리", stops[3].title)
    }

    @Test
    fun `투어와 항공은 날짜 고정으로 본다`() {
        val days = PlanTextParser.parse(aiPlan, today)

        assertEquals(PlanFixity.FIXED, days[0].stops[0].fixity)
        assertEquals(PlanFixity.FIXED, days[0].stops[1].fixity)
        assertEquals(PlanFixity.FIXED, days[1].stops[0].fixity)
    }

    @Test
    fun `그냥 둘러볼 곳은 자유다`() {
        val days = PlanTextParser.parse(aiPlan, today)

        assertEquals(PlanFixity.FLEXIBLE, days[0].stops[2].fixity)
        assertEquals(PlanFixity.FLEXIBLE, days[1].stops[1].fixity)
    }

    @Test
    fun `시각만 있으면 시간 있음으로 본다`() {
        val text = """
            1일차
            - 12:30 점심 식당 이름
        """.trimIndent()

        val stop = PlanTextParser.parse(text, today).first().stops.first()

        assertEquals(PlanFixity.TIMED, stop.fixity)
        assertEquals(LocalTime.of(12, 30), stop.startTime)
    }

    @Test
    fun `머리글 앞의 인사말은 버린다`() {
        val stops = PlanTextParser.parse(aiPlan, today).flatMap { it.stops }

        assertTrue(stops.none { it.title.contains("동선을") })
    }

    @Test
    fun `Day 1 처럼 영어로 적어도 읽는다`() {
        val text = """
            Day 1
            - 공항 도착
            Day 2
            - 시내 구경
        """.trimIndent()

        val days = PlanTextParser.parse(text, today)

        assertEquals(listOf(1, 2), days.map { it.dayNumber })
    }

    @Test
    fun `표로 그린 일정도 읽는다`() {
        val text = """
            1일차
            | 시간 | 장소 | 메모 |
            |---|---|---|
            | 09:00 | 성산일출봉 | 일출 |
            | 13:00 | 우도 투어 | 예약함 |
        """.trimIndent()

        val stops = PlanTextParser.parse(text, today).first().stops

        assertEquals(2, stops.size)
        assertEquals("성산일출봉", stops[0].title)
        assertEquals(LocalTime.of(9, 0), stops[0].startTime)
        assertEquals("일출", stops[0].memo)
        assertEquals(PlanFixity.FIXED, stops[1].fixity)
    }

    @Test
    fun `날짜가 없으면 일차로 여행 시작일에서 민다`() {
        val days = PlanTextParser.parse("Day 1\n- 가\nDay 3\n- 나", today)

        val dated = PlanTextParser.placeOn(days, tripStart, tripEnd)

        assertEquals(LocalDate.of(2026, 10, 1), dated[0].date)
        assertEquals(LocalDate.of(2026, 10, 3), dated[1].date)
        assertTrue(dated.all { it.insideTrip })
    }

    @Test
    fun `글에 적힌 해가 달라도 여행 기간에 맞춘다`() {
        // 해를 빼먹으면 올해로 읽힌다. 여행이 내년이면 그대로 쓸 수 없다.
        val days = listOf(PlannedDay(date = LocalDate.of(2025, 10, 2), stops = listOf(PlannedStop("가"))))

        val dated = PlanTextParser.placeOn(days, tripStart, tripEnd)

        assertEquals(LocalDate.of(2026, 10, 2), dated.first().date)
        assertTrue(dated.first().insideTrip)
    }

    @Test
    fun `여행 기간 밖의 날은 밖이라고 알려 준다`() {
        val days = listOf(PlannedDay(dayNumber = 9, stops = listOf(PlannedStop("가"))))

        val dated = PlanTextParser.placeOn(days, tripStart, tripEnd)

        assertEquals(LocalDate.of(2026, 10, 9), dated.first().date)
        assertTrue(!dated.first().insideTrip)
    }

    @Test
    fun `일정이 없는 글에서는 아무것도 읽지 않는다`() {
        assertTrue(PlanTextParser.parse("오늘 점심 뭐 먹지", today).isEmpty())
    }
}
