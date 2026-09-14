package com.volp.travelbudget.domain.booking

import com.volp.travelbudget.domain.textparse.TextScan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 공유로 들어온 글에서 읽어 낸 예약.
 *
 * 시각을 못 읽었을 수 있으므로 날짜와 시각을 따로 들고 있는다. 화면에서 사람이 마저 채운다.
 */
data class ParsedBooking(
    val type: BookingType = BookingType.OTHER,
    val title: String = "",
    val provider: String = "",
    val confirmationCode: String = "",
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val fromName: String = "",
    val fromCode: String = "",
    val toName: String = "",
    val toCode: String = "",
    val address: String = "",
    val seat: String = "",
    val memo: String = "",
    val sourceText: String = "",
) {
    fun startAt(default: LocalTime = LocalTime.of(9, 0)): LocalDateTime? =
        startDate?.atTime(startTime ?: default)

    fun endAt(default: LocalTime = LocalTime.of(11, 0)): LocalDateTime? =
        endDate?.atTime(endTime ?: default)

    /**
     * 예약으로 읽힌 정도.
     *
     * 같은 글을 구매로도 읽을 수 있어, 어느 쪽을 먼저 보여 줄지 정하는 데 쓴다.
     */
    val confidence: Int
        get() = listOf(
            type != BookingType.OTHER,
            startDate != null,
            startTime != null,
            fromCode.isNotBlank() && toCode.isNotBlank(),
            confirmationCode.isNotBlank(),
            seat.isNotBlank(),
            endDate != null,
        ).count { it }
}

/**
 * e-티켓이나 숙소 확인 문자를 예약으로 바꾼다.
 *
 * 공항에서 꺼내 보는 값(편명, 예약번호, 좌석, 출발 시각)을 집는 데 집중한다. 나머지는 원문에
 * 남겨 두면 되고, 잘못 읽은 자리는 편집 화면에서 바로잡을 수 있다.
 */
object BookingTextParser {

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedBooking {
        val cleaned = TextScan.clean(text)
        if (cleaned.isBlank()) return ParsedBooking(sourceText = text)

        val type = findType(cleaned)
        val route = findRoute(cleaned)
        val schedule = findSchedule(cleaned, type, today)

        return ParsedBooking(
            type = type,
            title = findTitle(cleaned, type, route),
            provider = findProvider(cleaned),
            confirmationCode = TextScan.codeNear(cleaned, CONFIRMATION_KEYWORDS),
            startDate = schedule.startDate,
            startTime = schedule.startTime,
            endDate = schedule.endDate,
            endTime = schedule.endTime,
            fromName = route?.fromName.orEmpty(),
            fromCode = route?.fromCode.orEmpty(),
            toName = route?.toName.orEmpty(),
            toCode = route?.toCode.orEmpty(),
            address = TextScan.valueAfterAny(cleaned, ADDRESS_KEYWORDS, ADDRESS_WINDOW)?.trim().orEmpty(),
            seat = TextScan.valueAfterAny(cleaned, SEAT_KEYWORDS, SEAT_WINDOW)?.trim().orEmpty(),
            sourceText = text.trim(),
        )
    }

    // ---- 무슨 예약인지 ----

    private val TYPE_WORDS = listOf(
        BookingType.FLIGHT to listOf("항공", "e-티켓", "eticket", "탑승", "편명", "항공편", "기내"),
        BookingType.LODGING to listOf("체크인", "체크아웃", "숙박", "호텔", "료칸", "게스트하우스", "객실"),
        BookingType.TRAIN to listOf("KTX", "SRT", "열차", "기차", "승차권"),
        BookingType.BUS to listOf("고속버스", "시외버스", "버스 예약"),
        BookingType.CAR to listOf("렌터카", "렌트카", "차량 인수"),
        BookingType.TICKET to listOf("입장권", "공연", "관람", "바우처", "티켓"),
    )

    private fun findType(text: String): BookingType {
        TYPE_WORDS.forEach { (type, words) ->
            if (words.any { text.contains(it, ignoreCase = true) }) return type
        }
        return BookingType.OTHER
    }

    // ---- 어디서 어디로 ----

    private data class Route(
        val fromName: String,
        val fromCode: String,
        val toName: String,
        val toCode: String,
    )

    /** `인천(ICN) - 간사이(KIX)` 또는 `ICN → NRT`. */
    private val NAMED_ROUTE = Regex(
        "([가-힣A-Za-z ]{1,12})\\(([A-Z]{3})\\)\\s*(?:→|->|-|~|~>|/)\\s*([가-힣A-Za-z ]{1,12})\\(([A-Z]{3})\\)",
    )
    private val CODE_ROUTE = Regex("(?<![A-Z])([A-Z]{3})\\s*(?:→|->|~)\\s*([A-Z]{3})(?![A-Z])")

    private fun findRoute(text: String): Route? {
        NAMED_ROUTE.find(text)?.let { match ->
            val (fromName, fromCode, toName, toCode) = match.destructured
            return Route(fromName.trim(), fromCode, toName.trim(), toCode)
        }
        CODE_ROUTE.find(text)?.let { match ->
            return Route("", match.groupValues[1], "", match.groupValues[2])
        }
        return null
    }

    // ---- 언제 ----

    private data class Schedule(
        val startDate: LocalDate?,
        val startTime: LocalTime?,
        val endDate: LocalDate?,
        val endTime: LocalTime?,
    )

    private val DEPARTURE_KEYWORDS = listOf("출발", "탑승", "출국", "승차")
    private val ARRIVAL_KEYWORDS = listOf("도착", "입국", "하차")
    private val CHECK_IN_KEYWORDS = listOf("체크인", "입실")
    private val CHECK_OUT_KEYWORDS = listOf("체크아웃", "퇴실")

    private fun findSchedule(text: String, type: BookingType, today: LocalDate): Schedule {
        val (startWords, endWords) = if (type == BookingType.LODGING) {
            CHECK_IN_KEYWORDS to CHECK_OUT_KEYWORDS
        } else {
            DEPARTURE_KEYWORDS to ARRIVAL_KEYWORDS
        }

        // 열쇳말이 없는 글도 있다. 그때는 글에 나온 첫 날짜를 출발로 본다.
        val startDate = TextScan.dateNear(text, startWords, today) ?: TextScan.dateIn(text, today)
        val startTime = TextScan.timeNear(text, startWords) ?: TextScan.timeIn(text)

        val endDate = TextScan.dateNear(text, endWords, today)
        val endTime = TextScan.timeNear(text, endWords)

        return Schedule(
            startDate = startDate,
            startTime = startTime,
            // 도착이 출발보다 앞설 수는 없다. 잘못 읽은 것이므로 버린다.
            endDate = endDate?.takeIf { startDate == null || !it.isBefore(startDate) },
            endTime = endTime,
        )
    }

    // ---- 무엇으로 부를지 ----

    private val FLIGHT_NUMBER = Regex("(?<![A-Z0-9])([A-Z]{2}|[0-9][A-Z])\\s?([0-9]{2,4})(?![0-9])")
    private val TITLE_KEYWORDS = listOf("숙소명", "호텔명", "숙소", "열차", "편명", "항공편", "상품명")

    private val AIRLINES = mapOf(
        "KE" to "대한항공", "OZ" to "아시아나항공", "7C" to "제주항공", "LJ" to "진에어",
        "TW" to "티웨이항공", "BX" to "에어부산", "RS" to "에어서울", "JL" to "일본항공",
        "NH" to "전일본공수", "CI" to "중화항공", "BR" to "에바항공", "CX" to "캐세이퍼시픽",
        "SQ" to "싱가포르항공", "TG" to "타이항공", "VN" to "베트남항공", "MU" to "중국동방항공",
        "CA" to "중국국제항공", "CZ" to "중국남방항공",
    )

    private fun findTitle(text: String, type: BookingType, route: Route?): String {
        if (type == BookingType.FLIGHT) {
            flightNumberIn(text)?.let { return it }
        }

        TextScan.valueAfterAny(text, TITLE_KEYWORDS)?.let { value ->
            val trimmed = value.trim().take(MAX_TITLE)
            if (trimmed.isNotBlank()) return trimmed
        }

        if (type == BookingType.LODGING) {
            lodgingNameIn(text)?.let { return it }
        }

        route?.let {
            if (it.fromCode.isNotBlank()) return "${it.fromCode} → ${it.toCode}"
        }

        return text.lineSequence()
            .map { TextScan.stripLeadingTag(it).trim() }
            .firstOrNull { it.length >= MIN_TITLE && !it.first().isDigit() }
            ?.take(MAX_TITLE)
            ?: type.label
    }

    /** 편명은 예약을 가리키는 가장 짧은 이름이다. 항공사 코드로 시작하는 것만 인정한다. */
    private fun flightNumberIn(text: String): String? =
        FLIGHT_NUMBER.findAll(text)
            .map { it.groupValues[1] to it.groupValues[2] }
            .firstOrNull { (prefix, _) -> prefix in AIRLINES }
            ?.let { (prefix, number) -> "$prefix$number" }

    private val LODGING_WORDS = listOf("호텔", "료칸", "게스트하우스", "레지던스", "인 ", "리조트")

    private fun lodgingNameIn(text: String): String? =
        text.lineSequence()
            .map { TextScan.stripLeadingTag(it).trim() }
            .firstOrNull { line -> LODGING_WORDS.any { line.contains(it) } }
            ?.take(MAX_TITLE)

    private fun findProvider(text: String): String {
        AIRLINES.forEach { (code, name) ->
            if (text.contains(name)) return name
            if (flightNumberIn(text)?.startsWith(code) == true) return name
        }
        PLATFORMS.forEach { name -> if (text.contains(name, ignoreCase = true)) return name }
        return ""
    }

    private val PLATFORMS = listOf(
        "아고다", "부킹닷컴", "에어비앤비", "야놀자", "여기어때", "호텔스컴바인",
        "익스피디아", "트립닷컴", "마이리얼트립", "클룩", "코레일", "SRT",
    )

    private val CONFIRMATION_KEYWORDS =
        listOf("예약번호", "예약 번호", "예약코드", "확인번호", "확약번호", "PNR", "발권번호")
    private val SEAT_KEYWORDS = listOf("좌석", "호실", "객실번호", "Seat")
    private val ADDRESS_KEYWORDS = listOf("주소", "위치", "Address")

    private const val MIN_TITLE = 2
    private const val MAX_TITLE = 60
    private const val SEAT_WINDOW = 12
    private const val ADDRESS_WINDOW = 60
}
