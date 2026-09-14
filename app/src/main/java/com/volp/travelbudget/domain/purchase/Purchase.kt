package com.volp.travelbudget.domain.purchase

import com.volp.travelbudget.domain.sync.Syncable
import java.time.LocalDate

/** 여행 전에 사 두는 것들의 갈래. */
enum class PurchaseKind(val label: String, val emoji: String) {
    FLIGHT("항공권", "✈️"),
    LODGING("숙소", "🏨"),
    TICKET("입장권·패스", "🎫"),
    TRANSPORT("교통·렌터카", "🚌"),
    GEAR("여행용품", "🧳"),
    CLOTHING("옷·신발", "👕"),
    ELECTRONICS("전자기기", "🔌"),
    INSURANCE("보험·로밍", "🛡️"),
    OTHER("그 밖에", "🛍️"),
    ;

    companion object {
        fun fromName(name: String?): PurchaseKind =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** 산 물건이 지금 어디쯤 와 있는지. */
enum class PurchaseStatus(val label: String) {
    ORDERED("주문함"),
    SHIPPED("배송 중"),
    ARRIVED("도착함"),
    CANCELLED("취소함"),
    ;

    val isOpen: Boolean get() = this == ORDERED || this == SHIPPED

    companion object {
        fun fromName(name: String?): PurchaseStatus =
            entries.firstOrNull { it.name == name } ?: ORDERED
    }
}

/**
 * 여행 전에 산 것 한 건.
 *
 * 가계부의 지출과 달리 **언제 오는지**가 중요하다. 출발 전에 못 받으면 여행에 못 들고 가므로
 * 도착 예정일([eta])과 여행 시작일을 견주는 것이 이 기록의 쓸모다.
 */
data class Purchase(
    val id: Long = 0L,
    override val uid: String = "",
    override val updatedAt: Long = 0L,
    /** 어느 여행 것인지. 아직 안 정했으면 null. */
    val tripId: Long? = null,
    val kind: PurchaseKind = PurchaseKind.OTHER,
    val title: String,
    /** 판매처. 쿠팡, 대한항공처럼 어디서 샀는지. */
    val merchant: String = "",
    val amountKrw: Long = 0L,
    /** 외화로 샀으면 그 금액. 원화면 null. */
    val originalAmount: Double? = null,
    val currencyCode: String = "KRW",
    val orderedOn: LocalDate? = null,
    /** 도착 예정일. 모르면 null. */
    val eta: LocalDate? = null,
    val status: PurchaseStatus = PurchaseStatus.ORDERED,
    val orderNumber: String = "",
    val trackingNumber: String = "",
    val carrier: String = "",
    val memo: String = "",
    /** 공유로 들어온 원문. 잘못 읽었을 때 사람이 확인할 수 있게 남긴다. */
    val sourceText: String = "",
    /** 가계부에 이미 넣었으면 그 지출의 id. */
    val expenseId: Long? = null,
    val createdAt: Long = 0L,
) : Syncable {

    /** 오늘 기준 도착까지 남은 날. 예정일이 없으면 null, 지났으면 음수. */
    fun daysUntilEta(today: LocalDate): Long? =
        eta?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }

    /** 예정일이 지났는데 아직 안 온 것. */
    fun isOverdue(today: LocalDate): Boolean =
        status.isOpen && eta != null && eta.isBefore(today)

    /**
     * 출발 전에 못 받을 것 같은지.
     *
     * 예정일이 출발일 당일이면 아슬아슬하므로 이것도 위험으로 본다.
     */
    fun arrivesLate(tripStart: LocalDate): Boolean =
        status.isOpen && eta != null && !eta.isBefore(tripStart)

    /** 가계부에 넣을 수 있는 상태인지. 취소했거나 이미 넣었으면 아니다. */
    val canBecomeExpense: Boolean
        get() = status != PurchaseStatus.CANCELLED && expenseId == null && amountKrw > 0L
}

object Purchases {

    /**
     * 도착이 급한 것부터 늘어놓는다.
     *
     * 예정일을 모르는 것은 재촉할 수 없으므로 맨 뒤로 보내고, 이미 끝난 것도 아래로 내린다.
     */
    fun sortByUrgency(items: List<Purchase>): List<Purchase> =
        items.sortedWith(
            compareBy<Purchase> { !it.status.isOpen }
                .thenBy { it.eta ?: LocalDate.MAX }
                .thenByDescending { it.createdAt },
        )

    /** 출발 전에 못 받을 것 같은 것들. 여행 화면에서 빨갛게 보여 준다. */
    fun risky(items: List<Purchase>, tripStart: LocalDate): List<Purchase> =
        items.filter { it.arrivesLate(tripStart) }

    /** 아직 오지 않은 것들의 합. 예산에서 이미 나간 돈을 가늠할 때 쓴다. */
    fun openTotalKrw(items: List<Purchase>): Long =
        items.filter { it.status.isOpen }.sumOf { it.amountKrw }
}
