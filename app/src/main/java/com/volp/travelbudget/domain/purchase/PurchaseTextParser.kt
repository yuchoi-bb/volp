package com.volp.travelbudget.domain.purchase

import java.time.LocalDate

/**
 * 공유로 들어온 글 한 덩어리에서 읽어 낸 값.
 *
 * 못 읽은 자리는 비워 둔다. 화면에서 사람이 마저 채우는 것을 전제로 한다.
 */
data class ParsedPurchase(
    val title: String = "",
    val merchant: String = "",
    val kind: PurchaseKind = PurchaseKind.OTHER,
    val amountKrw: Long = 0L,
    val originalAmount: Double? = null,
    val currencyCode: String = "KRW",
    val orderedOn: LocalDate? = null,
    val eta: LocalDate? = null,
    val orderNumber: String = "",
    val trackingNumber: String = "",
    val carrier: String = "",
    val status: PurchaseStatus = PurchaseStatus.ORDERED,
    val sourceText: String = "",
) {
    /** 사람이 손대지 않아도 될 만큼 읽었는지. 제목과 금액이면 충분하다. */
    val isUsable: Boolean get() = title.isNotBlank() && (amountKrw > 0L || eta != null)
}

/**
 * 주문·배송 문자나 복사해 온 글을 구매 기록으로 바꾼다.
 *
 * 쇼핑몰마다 문구가 제각각이라 서식을 외우는 대신 **열쇳말 옆의 값**을 줍는 방식으로 읽는다.
 * 틀리게 읽어도 원문을 함께 남기므로 화면에서 바로잡을 수 있다.
 */
object PurchaseTextParser {

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedPurchase {
        val cleaned = clean(text)
        if (cleaned.isBlank()) return ParsedPurchase(sourceText = text)

        val vendor = findVendor(cleaned)
        val money = findMoney(cleaned)
        val kind = findKind(cleaned, vendor?.kind)

        return ParsedPurchase(
            title = findTitle(cleaned, vendor?.name.orEmpty(), kind),
            merchant = vendor?.name ?: findMerchantInBrackets(cleaned),
            kind = kind,
            amountKrw = money?.krw ?: 0L,
            originalAmount = money?.foreign,
            currencyCode = money?.currency ?: "KRW",
            orderedOn = findDate(cleaned, ORDER_KEYWORDS, today),
            eta = findDate(cleaned, ETA_KEYWORDS, today),
            orderNumber = findCode(cleaned, ORDER_NUMBER_KEYWORDS),
            trackingNumber = findCode(cleaned, TRACKING_KEYWORDS),
            carrier = CARRIERS.firstOrNull { cleaned.contains(it) }.orEmpty(),
            status = findStatus(cleaned),
            sourceText = text.trim(),
        )
    }

    // ---- 다듬기 ----

    private fun clean(text: String): String = text
        .replace("[Web발신]", " ")
        .replace("[국제발신]", " ")
        .replace(' ', ' ')
        .trim()

    // ---- 판매처와 갈래 ----

    private data class Vendor(val name: String, val keywords: List<String>, val kind: PurchaseKind?)

    private val VENDORS = listOf(
        Vendor("대한항공", listOf("대한항공", "KOREAN AIR"), PurchaseKind.FLIGHT),
        Vendor("아시아나항공", listOf("아시아나"), PurchaseKind.FLIGHT),
        Vendor("제주항공", listOf("제주항공"), PurchaseKind.FLIGHT),
        Vendor("티웨이항공", listOf("티웨이"), PurchaseKind.FLIGHT),
        Vendor("진에어", listOf("진에어"), PurchaseKind.FLIGHT),
        Vendor("에어부산", listOf("에어부산"), PurchaseKind.FLIGHT),
        Vendor("네이버항공권", listOf("네이버항공권"), PurchaseKind.FLIGHT),
        Vendor("아고다", listOf("아고다", "Agoda"), PurchaseKind.LODGING),
        Vendor("부킹닷컴", listOf("부킹닷컴", "Booking.com"), PurchaseKind.LODGING),
        Vendor("에어비앤비", listOf("에어비앤비", "Airbnb"), PurchaseKind.LODGING),
        Vendor("야놀자", listOf("야놀자"), PurchaseKind.LODGING),
        Vendor("여기어때", listOf("여기어때"), PurchaseKind.LODGING),
        Vendor("클룩", listOf("클룩", "Klook"), PurchaseKind.TICKET),
        Vendor("마이리얼트립", listOf("마이리얼트립"), PurchaseKind.TICKET),
        Vendor("쿠팡", listOf("쿠팡", "coupang"), null),
        Vendor("네이버페이", listOf("네이버페이", "스마트스토어"), null),
        Vendor("11번가", listOf("11번가"), null),
        Vendor("G마켓", listOf("G마켓", "지마켓"), null),
        Vendor("옥션", listOf("옥션"), null),
        Vendor("무신사", listOf("무신사"), PurchaseKind.CLOTHING),
        Vendor("알리익스프레스", listOf("알리익스프레스", "AliExpress"), null),
        Vendor("테무", listOf("테무", "Temu"), null),
        Vendor("컬리", listOf("컬리"), null),
        Vendor("올리브영", listOf("올리브영"), null),
    )

    private fun findVendor(text: String): Vendor? =
        VENDORS.firstOrNull { vendor -> vendor.keywords.any { text.contains(it, ignoreCase = true) } }

    private val KIND_WORDS = listOf(
        PurchaseKind.FLIGHT to listOf("항공권", "항공편", "e-티켓", "eticket", "탑승권", "왕복", "편도"),
        PurchaseKind.LODGING to listOf("숙박", "호텔", "체크인", "게스트하우스", "료칸"),
        PurchaseKind.TICKET to listOf("입장권", "티켓", "패스", "투어", "예약확정", "바우처"),
        PurchaseKind.TRANSPORT to listOf("렌터카", "철도", "기차", "버스", "공항버스", "픽업"),
        PurchaseKind.INSURANCE to listOf("여행자보험", "보험", "로밍", "유심", "eSIM", "데이터"),
        PurchaseKind.ELECTRONICS to listOf("보조배터리", "어댑터", "충전기", "이어폰", "카메라", "멀티탭", "변환플러그"),
        PurchaseKind.GEAR to listOf("캐리어", "여행가방", "파우치", "목베개", "압축팩", "백팩", "크로스백"),
        PurchaseKind.CLOTHING to listOf("자켓", "재킷", "운동화", "신발", "티셔츠", "바지", "수영복", "래시가드"),
    )

    private fun findKind(text: String, vendorKind: PurchaseKind?): PurchaseKind {
        // 물건 이름이 판매처보다 구체적이다. 쿠팡에서 산 캐리어는 쿠팡이 아니라 여행용품이다.
        KIND_WORDS.forEach { (kind, words) ->
            if (words.any { text.contains(it, ignoreCase = true) }) return kind
        }
        return vendorKind ?: PurchaseKind.OTHER
    }

    // ---- 제목 ----

    private val TITLE_KEYWORDS = listOf("상품명", "품명", "상품", "주문상품", "여정", "노선")

    private fun findTitle(text: String, merchant: String, kind: PurchaseKind): String {
        TITLE_KEYWORDS.forEach { keyword ->
            valueAfter(text, keyword)?.let { value ->
                val trimmed = value.trim().take(MAX_TITLE)
                if (trimmed.isNotBlank()) return trimmed
            }
        }

        val firstLine = text.lineSequence()
            .map { stripLeadingTag(it).trim() }
            .firstOrNull { it.length >= MIN_TITLE && !it.first().isDigit() }

        return firstLine?.take(MAX_TITLE)
            ?: merchant.ifBlank { kind.label }
    }

    /** 문자 앞머리의 `[쿠팡]` 같은 꼬리표를 뗀다. */
    private fun stripLeadingTag(line: String): String =
        line.replace(Regex("^\\s*\\[[^\\]]{1,20}\\]\\s*"), "")

    private fun findMerchantInBrackets(text: String): String =
        Regex("^\\s*\\[([^\\]]{1,20})\\]").find(text)?.groupValues?.get(1)?.trim().orEmpty()

    // ---- 금액 ----

    private data class Money(val krw: Long, val foreign: Double?, val currency: String)

    private val AMOUNT_KEYWORDS =
        listOf("총 결제금액", "총결제금액", "결제금액", "결제 금액", "총액", "합계", "결제액", "금액", "가격")

    private val KRW = Regex("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})\\s*(?:원|KRW|₩)")
    private val KRW_SYMBOL = Regex("₩\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})")
    private val FOREIGN = Regex(
        "(USD|EUR|JPY|CNY|GBP|AUD|CAD|CHF|HKD|SGD|THB|VND|TWD)\\s*([0-9,]+(?:\\.[0-9]+)?)" +
            "|([0-9,]+(?:\\.[0-9]+)?)\\s*(USD|EUR|JPY|CNY|GBP|AUD|CAD|CHF|HKD|SGD|THB|VND|TWD)" +
            "|\\$\\s*([0-9,]+(?:\\.[0-9]+)?)",
    )

    private fun findMoney(text: String): Money? {
        // 열쇳말 뒤에 붙은 금액이 가장 믿을 만하다. 할인 전 가격이나 적립금에 속지 않는다.
        AMOUNT_KEYWORDS.forEach { keyword ->
            val index = text.indexOf(keyword)
            if (index >= 0) {
                val tail = text.substring(index + keyword.length).take(NEAR_WINDOW)
                krwIn(tail)?.let { return Money(it, null, "KRW") }
            }
        }

        foreignIn(text)?.let { return it }
        krwIn(text)?.let { return Money(it, null, "KRW") }
        return null
    }

    private fun krwIn(text: String): Long? {
        val matches = (KRW.findAll(text) + KRW_SYMBOL.findAll(text))
            .mapNotNull { it.groupValues.drop(1).firstOrNull { g -> g.isNotBlank() } }
            .mapNotNull { it.replace(",", "").toLongOrNull() }
            .toList()
        return matches.maxOrNull()
    }

    private fun foreignIn(text: String): Money? {
        val match = FOREIGN.find(text) ?: return null
        val groups = match.groupValues
        val currency = when {
            groups[1].isNotBlank() -> groups[1]
            groups[4].isNotBlank() -> groups[4]
            else -> "USD"
        }
        val raw = listOf(groups[2], groups[3], groups[5]).firstOrNull { it.isNotBlank() } ?: return null
        val amount = raw.replace(",", "").toDoubleOrNull() ?: return null
        // 원화 환산은 환율을 아는 쪽에서 한다. 여기서는 통화와 금액만 넘긴다.
        return Money(krw = 0L, foreign = amount, currency = currency.uppercase())
    }

    // ---- 날짜 ----

    private val ETA_KEYWORDS = listOf(
        "도착 예정", "도착예정", "배송 예정", "배송예정", "수령 예정", "수령예정",
        "출고 예정", "출고예정", "배달 예정", "배달예정", "예정일", "도착일", "ETA", "도착",
    )

    private val ORDER_KEYWORDS = listOf("주문일", "주문 일자", "결제일", "구매일", "주문일시", "결제 일시")

    private val FULL_DATE = Regex("(20[0-9]{2})[-./년]\\s*([0-9]{1,2})[-./월]\\s*([0-9]{1,2})")
    private val MONTH_DAY = Regex("(?<![0-9])([0-9]{1,2})\\s*(?:월\\s*([0-9]{1,2})\\s*일|[/.]\\s*([0-9]{1,2}))(?![0-9])")

    /**
     * 열쇳말 뒤에서 가장 가까운 날짜를 찾는다.
     *
     * `10/12`처럼 해가 없는 날짜는 오늘을 기준으로 가까운 쪽을 고른다. 지난달 날짜로 읽히면
     * 배송 예정일로는 말이 되지 않으므로 다음 해로 넘긴다.
     */
    private fun findDate(text: String, keywords: List<String>, today: LocalDate): LocalDate? {
        keywords.forEach { keyword ->
            var from = text.indexOf(keyword)
            while (from >= 0) {
                val tail = text.substring(from + keyword.length).take(NEAR_WINDOW)
                dateIn(tail, today)?.let { return it }
                // 같은 열쇳말이 여러 번 나오면 값이 붙은 쪽을 계속 찾는다.
                val head = text.substring(maxOf(0, from - NEAR_WINDOW), from)
                dateIn(head, today)?.let { return it }
                from = text.indexOf(keyword, from + keyword.length)
            }
        }
        return null
    }

    private fun dateIn(text: String, today: LocalDate): LocalDate? {
        FULL_DATE.findAll(text).forEach { match ->
            val (year, month, day) = match.destructured
            safeDate(year.toInt(), month.toInt(), day.toInt())?.let { return it }
        }
        MONTH_DAY.findAll(text).forEach { match ->
            val month = match.groupValues[1].toIntOrNull()
            val day = (match.groupValues[2].ifBlank { match.groupValues[3] }).toIntOrNull()
            // `12.500` 같은 금액이 12월 500일로 읽히지 않게 달과 날의 범위를 먼저 본다.
            val candidate = if (month == null || day == null) null else safeDate(today.year, month, day)
            if (candidate != null) {
                return if (candidate.isBefore(today.minusDays(PAST_TOLERANCE_DAYS))) {
                    candidate.plusYears(1)
                } else {
                    candidate
                }
            }
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    // ---- 번호 ----

    private val ORDER_NUMBER_KEYWORDS = listOf("주문번호", "주문 번호", "예약번호", "예약 번호", "예약코드", "확인번호")
    private val TRACKING_KEYWORDS = listOf("송장번호", "송장 번호", "운송장번호", "운송장 번호", "등기번호")
    private val CARRIERS = listOf("CJ대한통운", "대한통운", "우체국", "한진택배", "한진", "롯데택배", "롯데", "로젠", "쿠팡친구", "로켓배송")
    private val CODE = Regex("[A-Za-z0-9][A-Za-z0-9-]{5,25}")

    private fun findCode(text: String, keywords: List<String>): String {
        keywords.forEach { keyword ->
            valueAfter(text, keyword)?.let { tail ->
                CODE.find(tail)?.value?.let { return it }
            }
        }
        return ""
    }

    /** `열쇳말 : 값` 꼴에서 값 쪽을 잘라 온다. */
    private fun valueAfter(text: String, keyword: String): String? {
        val index = text.indexOf(keyword)
        if (index < 0) return null
        return text.substring(index + keyword.length)
            .trimStart(' ', ':', '=', '-', '\t')
            .lineSequence()
            .firstOrNull()
            ?.take(NEAR_WINDOW)
    }

    // ---- 상태 ----

    private fun findStatus(text: String): PurchaseStatus = when {
        text.contains("취소") -> PurchaseStatus.CANCELLED
        text.contains("배송완료") || text.contains("배송 완료") || text.contains("수령완료") ->
            PurchaseStatus.ARRIVED
        text.contains("배송 시작") || text.contains("배송시작") || text.contains("발송") ||
            text.contains("출고") || text.contains("상품이 출발") -> PurchaseStatus.SHIPPED
        else -> PurchaseStatus.ORDERED
    }

    private const val NEAR_WINDOW = 40
    private const val MIN_TITLE = 2
    private const val MAX_TITLE = 60
    private const val PAST_TOLERANCE_DAYS = 30L
}
