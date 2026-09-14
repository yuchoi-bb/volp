package com.volp.travelbudget.domain.purchase

import com.volp.travelbudget.domain.textparse.TextScan
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

    /**
     * 구매로 읽힌 정도.
     *
     * 같은 글을 예약으로도 읽을 수 있어, 어느 쪽으로 먼저 보여 줄지 정하는 데 쓴다.
     */
    val confidence: Int
        get() = listOf(
            amountKrw > 0L || originalAmount != null,
            eta != null,
            trackingNumber.isNotBlank(),
            carrier.isNotBlank(),
            merchant.isNotBlank(),
            orderNumber.isNotBlank(),
        ).count { it }
}

/**
 * 주문·배송 문자나 복사해 온 글을 구매 기록으로 바꾼다.
 *
 * 틀리게 읽어도 원문을 함께 남기므로 화면에서 바로잡을 수 있다.
 */
object PurchaseTextParser {

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedPurchase {
        val cleaned = TextScan.clean(text)
        if (cleaned.isBlank()) return ParsedPurchase(sourceText = text)

        val vendor = findVendor(cleaned)
        val money = findMoney(cleaned)
        val kind = findKind(cleaned, vendor?.kind)

        return ParsedPurchase(
            title = findTitle(cleaned, vendor?.name.orEmpty(), kind),
            merchant = vendor?.name ?: TextScan.leadingTag(cleaned),
            kind = kind,
            amountKrw = money?.krw ?: 0L,
            originalAmount = money?.foreign,
            currencyCode = money?.currency ?: "KRW",
            orderedOn = TextScan.dateNear(cleaned, ORDER_KEYWORDS, today),
            eta = TextScan.dateNear(cleaned, ETA_KEYWORDS, today),
            orderNumber = TextScan.codeNear(cleaned, ORDER_NUMBER_KEYWORDS),
            trackingNumber = TextScan.codeNear(cleaned, TRACKING_KEYWORDS),
            carrier = CARRIERS.firstOrNull { cleaned.contains(it) }.orEmpty(),
            status = findStatus(cleaned),
            sourceText = text.trim(),
        )
    }

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
        TextScan.valueAfterAny(text, TITLE_KEYWORDS)?.let { value ->
            val trimmed = value.trim().take(MAX_TITLE)
            if (trimmed.isNotBlank()) return trimmed
        }

        val firstLine = text.lineSequence()
            .map { TextScan.stripLeadingTag(it).trim() }
            .firstOrNull { it.length >= MIN_TITLE && !it.first().isDigit() }

        return firstLine?.take(MAX_TITLE) ?: merchant.ifBlank { kind.label }
    }

    // ---- 금액 ----

    private data class Money(val krw: Long, val foreign: Double?, val currency: String)

    private val AMOUNT_KEYWORDS =
        listOf("총 결제금액", "총결제금액", "결제금액", "결제 금액", "총액", "합계", "결제액", "금액", "가격")

    private fun findMoney(text: String): Money? {
        // 열쇳말 뒤에 붙은 금액이 가장 믿을 만하다. 할인 전 가격이나 적립금에 속지 않는다.
        AMOUNT_KEYWORDS.forEach { keyword ->
            val index = text.indexOf(keyword)
            if (index >= 0) {
                val tail = text.substring(index + keyword.length).take(TextScan.WINDOW)
                TextScan.krwIn(tail)?.let { return Money(it, null, "KRW") }
            }
        }

        // 원화 환산은 환율을 아는 쪽에서 한다. 여기서는 통화와 금액만 넘긴다.
        TextScan.foreignIn(text)?.let { (currency, amount) -> return Money(0L, amount, currency) }
        TextScan.krwIn(text)?.let { return Money(it, null, "KRW") }
        return null
    }

    // ---- 날짜와 번호 ----

    private val ETA_KEYWORDS = listOf(
        "도착 예정", "도착예정", "배송 예정", "배송예정", "수령 예정", "수령예정",
        "출고 예정", "출고예정", "배달 예정", "배달예정", "예정일", "도착일", "ETA", "도착",
    )

    private val ORDER_KEYWORDS = listOf("주문일", "주문 일자", "결제일", "구매일", "주문일시", "결제 일시")
    private val ORDER_NUMBER_KEYWORDS = listOf("주문번호", "주문 번호", "예약번호", "예약 번호", "예약코드", "확인번호")
    private val TRACKING_KEYWORDS = listOf("송장번호", "송장 번호", "운송장번호", "운송장 번호", "등기번호")
    private val CARRIERS =
        listOf("CJ대한통운", "대한통운", "우체국", "한진택배", "한진", "롯데택배", "롯데", "로젠", "쿠팡친구", "로켓배송")

    // ---- 상태 ----

    private fun findStatus(text: String): PurchaseStatus = when {
        text.contains("취소") -> PurchaseStatus.CANCELLED
        text.contains("배송완료") || text.contains("배송 완료") || text.contains("수령완료") ->
            PurchaseStatus.ARRIVED
        text.contains("배송 시작") || text.contains("배송시작") || text.contains("발송") ||
            text.contains("출고") || text.contains("상품이 출발") -> PurchaseStatus.SHIPPED
        else -> PurchaseStatus.ORDERED
    }

    private const val MIN_TITLE = 2
    private const val MAX_TITLE = 60
}
