package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.textparse.TextScan
import java.time.LocalDate
import java.time.LocalTime

/**
 * 일정 한 줄을 얼마나 옮기기 어려운지.
 *
 * 투어나 항공편은 한번 잡으면 날짜를 바꾸기 어렵다. 그 옆의 '카페 거리 산책'은 아무 날에나 놓아도
 * 된다. 둘을 같은 줄로 보여 주면 일정을 손볼 때 무엇을 먼저 맞춰야 하는지 알 수 없다.
 */
enum class PlanFixity(val label: String, val emoji: String) {
    /** 예약이 날짜에 묶인다. 이것부터 자리를 잡고 나머지를 맞춘다. */
    FIXED("날짜 고정", "🔒"),

    /** 시각이 정해져 있다. 날짜는 옮길 수 있어도 그날 안에서는 자리가 있다. */
    TIMED("시간 있음", "⏰"),

    /** 아무 때나. */
    FLEXIBLE("자유", ""),
    ;

    companion object {
        fun fromName(name: String?): PlanFixity =
            entries.firstOrNull { it.name == name } ?: FLEXIBLE
    }
}

/** 읽어 낸 일정 한 줄. */
data class PlannedStop(
    val title: String,
    val startTime: LocalTime? = null,
    val memo: String = "",
    val fixity: PlanFixity = PlanFixity.FLEXIBLE,
)

/** 읽어 낸 하루. 날짜가 적혀 있지 않으면 몇 일차인지만 안다. */
data class PlannedDay(
    val dayNumber: Int? = null,
    val date: LocalDate? = null,
    val heading: String = "",
    val stops: List<PlannedStop> = emptyList(),
)

/** 여행 날짜까지 정해진 하루. */
data class DatedPlanDay(
    val date: LocalDate,
    val stops: List<PlannedStop>,
    /** 여행 기간 안에 들어오는 날인지. 밖이면 넣기 전에 사람이 한 번 봐야 한다. */
    val insideTrip: Boolean,
)

/**
 * AI가 짜 준 일정 글을 읽어 하루씩 나눈다.
 *
 * Claude나 제미나이에 "3박 4일 제주 일정 짜 줘"라고 하면 날짜별 목록이 돌아온다. 그것을 손으로
 * 다시 치는 것은 헛일이다. 그런데 서식이 매번 다르다 — `## 1일차`, `Day 1`, `10/1 (목)`,
 * 표로 그린 것까지 있다. 그래서 서식을 외우는 대신 **하루의 머리글로 보이는 줄**과 **그 아래의
 * 항목 줄**만 가려낸다.
 *
 * 읽은 뒤에도 앱이 바로 넣지 않는다. 사람이 목록을 보고 고른 것만 들어간다.
 */
object PlanTextParser {

    /** 이보다 긴 줄은 설명하는 문장으로 본다. 목록 기호가 붙어 있으면 길어도 항목으로 본다. */
    private const val PROSE_LENGTH = 80

    private val DAY_NUMBER = listOf(
        Regex("""^day\s*([0-9]{1,2})\b""", RegexOption.IGNORE_CASE),
        Regex("""^([0-9]{1,2})\s*일\s*차"""),
        Regex("""^([0-9]{1,2})\s*일\s*째"""),
        Regex("""^제\s*([0-9]{1,2})\s*일"""),
    )

    private val ORDINAL_DAY = mapOf(
        "첫째" to 1, "첫" to 1, "둘째" to 2, "이틀째" to 2, "셋째" to 3, "사흘째" to 3,
        "넷째" to 4, "나흘째" to 4, "다섯째" to 5, "여섯째" to 6, "일곱째" to 7,
        "여덟째" to 8, "아홉째" to 9, "열째" to 10,
    )

    /** 예약이 날짜에 묶이는 것들. 한번 잡으면 바꾸기 어렵다. */
    private val FIXED_WORDS = listOf(
        "투어", "TOUR", "티켓", "입장권", "예매", "예약", "공연", "콘서트", "뮤지컬", "경기",
        "항공", "비행", "탑승", "출국", "귀국", "기차", "열차", "KTX", "신칸센", "페리",
        "크루즈", "유람선", "픽업", "체크인", "체크아웃", "가이드", "셔틀", "렌터카", "액티비티",
    )

    /** 때를 가리키기만 하는 말. 제목에서 떼어 낸다. */
    private val PERIOD_WORDS = listOf(
        "새벽", "아침", "오전", "점심", "낮", "오후", "저녁", "밤", "야간",
        "MORNING", "AFTERNOON", "EVENING", "NIGHT",
    )

    private val BULLET = Regex("""^\s*(?:[-*•·▪◦‣–—>]+|\(?[0-9]{1,2}[.)]|[①-⑳])\s*""")
    private val MARKDOWN = Regex("""[*_`#]+""")
    private val SEPARATOR = Regex("""^[\s\-=_|:]+$""")

    fun parse(text: String, today: LocalDate = LocalDate.now()): List<PlannedDay> {
        val days = mutableListOf<PlannedDay>()
        var current: PlannedDay? = null
        val stops = mutableListOf<PlannedStop>()

        fun close() {
            val day = current ?: return
            if (stops.isNotEmpty()) days += day.copy(stops = stops.toList())
            stops.clear()
        }

        text.lineSequence().forEach { raw ->
            val line = tidy(raw)
            if (line.isBlank() || SEPARATOR.matches(line)) return@forEach

            val header = dayHeader(line, today)
            if (header != null) {
                close()
                current = header
                return@forEach
            }

            // 머리글을 만나기 전의 줄은 인사말이다.
            if (current == null) return@forEach
            stop(raw, line)?.let { stops += it }
        }
        close()

        return days
    }

    /**
     * 읽어 낸 하루들에 실제 날짜를 붙인다.
     *
     * 글에 날짜가 적혀 있으면 그것을 쓰되 해는 여행에 맞춘다. AI는 해를 빼먹거나 올해로 적는다.
     * 날짜가 없으면 몇 일차인지로 여행 시작일에서 민다.
     */
    fun placeOn(days: List<PlannedDay>, tripStart: LocalDate, tripEnd: LocalDate): List<DatedPlanDay> =
        days.mapIndexed { index, day ->
            val date = when {
                day.date != null -> alignYear(day.date, tripStart, tripEnd)
                day.dayNumber != null -> tripStart.plusDays((day.dayNumber - 1).toLong())
                else -> tripStart.plusDays(index.toLong())
            }
            DatedPlanDay(
                date = date,
                stops = day.stops,
                insideTrip = !date.isBefore(tripStart) && !date.isAfter(tripEnd),
            )
        }

    // ---- 줄 읽기 ----

    private fun tidy(raw: String): String =
        raw.replace(' ', ' ').replace(MARKDOWN, " ").trim().trimEnd(':', '：')

    private fun dayHeader(line: String, today: LocalDate): PlannedDay? {
        val head = line.replace(BULLET, "")
        val number = DAY_NUMBER.firstNotNullOfOrNull { it.find(head)?.groupValues?.get(1)?.toIntOrNull() }
            ?: ORDINAL_DAY.entries.firstOrNull { head.startsWith(it.key) && head.contains("날") }?.value

        val date = TextScan.dateIn(head, today)

        return when {
            number != null -> PlannedDay(dayNumber = number, date = date, heading = head)
            // 날짜만 적힌 짧은 줄도 하루의 머리글이다. 긴 줄은 항목일 수 있어 건드리지 않는다.
            date != null && head.length <= DATE_HEADER_LENGTH -> PlannedDay(date = date, heading = head)
            else -> null
        }
    }

    private const val DATE_HEADER_LENGTH = 30

    private fun stop(raw: String, line: String): PlannedStop? {
        if (line.startsWith("|")) return tableStop(line)

        val bulleted = BULLET.containsMatchIn(raw)
        val body = line.replace(BULLET, "").trim()
        if (body.isBlank()) return null
        // 목록 기호가 없는 긴 줄은 설명이다.
        if (!bulleted && body.length > PROSE_LENGTH) return null

        val time = TextScan.timeIn(body)
        val (title, memo) = split(stripTime(body, time))
        if (title.isBlank()) return null

        return PlannedStop(
            title = title,
            startTime = time,
            memo = memo,
            fixity = fixityOf(line, time),
        )
    }

    /** `| 09:00 | 성산일출봉 | 입장료 |` 같은 표 한 줄. */
    private fun tableStop(line: String): PlannedStop? {
        val cells = line.trim('|').split("|").map { it.trim() }.filter { it.isNotBlank() }
        if (cells.isEmpty()) return null
        // 머리글 줄은 넘긴다.
        if (cells.any { it == "시간" || it == "장소" || it.equals("time", true) }) return null

        val time = TextScan.timeIn(cells.first())
        val rest = if (time != null) cells.drop(1) else cells
        val title = stripPeriod(rest.firstOrNull().orEmpty())
        if (title.isBlank()) return null

        return PlannedStop(
            title = title,
            startTime = time,
            memo = rest.drop(1).joinToString(" · "),
            fixity = fixityOf(line, time),
        )
    }

    private fun stripTime(body: String, time: LocalTime?): String {
        if (time == null) return body
        // 시각이 적힌 자리를 지운다. `09:00~11:00`처럼 두 번 적힌 것도 함께 지운다.
        return body
            .replace(Regex("""[0-9]{1,2}\s*:\s*[0-9]{2}"""), " ")
            .replace(Regex("""(오전|오후)?\s*[0-9]{1,2}\s*시(\s*[0-9]{1,2}\s*분)?"""), " ")
            .replace(Regex("""^[\s~\-–—.,)]+"""), "")
            .trim()
    }

    private fun split(body: String): Pair<String, String> {
        val cleaned = stripPeriod(body)
        val marks = listOf(" — ", " – ", " - ", " · ", ": ", "：", "(", "[")
        val index = marks.mapNotNull { mark ->
            cleaned.indexOf(mark).takeIf { it > 0 }?.let { it to mark.length }
        }.minByOrNull { it.first }

        if (index == null) return cleaned.squeeze() to ""

        val (at, markLength) = index
        val title = cleaned.take(at).squeeze()
        val memo = cleaned.drop(at + markLength).trim().trim(')', ']').squeeze()
        return if (title.isBlank()) cleaned.squeeze() to "" else title to memo
    }

    /** 앞머리의 `오전`, `저녁` 같은 말을 뗀다. 그 자체로는 일정이 아니다. */
    private fun stripPeriod(body: String): String {
        var result = body.trim()
        PERIOD_WORDS.forEach { word ->
            if (result.startsWith(word, ignoreCase = true)) {
                result = result.drop(word.length).trimStart(' ', ':', '-', '~', '–', '—', '·', '：')
            }
        }
        return result.trim()
    }

    private fun fixityOf(line: String, time: LocalTime?): PlanFixity {
        val upper = line.uppercase()
        return when {
            FIXED_WORDS.any { upper.contains(it.uppercase()) } -> PlanFixity.FIXED
            time != null -> PlanFixity.TIMED
            else -> PlanFixity.FLEXIBLE
        }
    }

    private fun String.squeeze(): String = replace(Regex("""\s+"""), " ").trim()

    private fun alignYear(date: LocalDate, tripStart: LocalDate, tripEnd: LocalDate): LocalDate {
        if (!date.isBefore(tripStart) && !date.isAfter(tripEnd)) return date
        listOf(tripStart.year, tripEnd.year, tripStart.year + 1).forEach { year ->
            val moved = runCatching { date.withYear(year) }.getOrNull() ?: return@forEach
            if (!moved.isBefore(tripStart) && !moved.isAfter(tripEnd)) return moved
        }
        return date
    }
}
