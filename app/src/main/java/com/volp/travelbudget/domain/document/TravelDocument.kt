package com.volp.travelbudget.domain.document

import com.volp.travelbudget.domain.sync.Syncable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DocumentKind(val label: String, val emoji: String) {
    PASSPORT("여권", "🛂"),
    VISA("비자", "📑"),
    INSURANCE("여행자보험", "🛡️"),
    TICKET("항공권·승차권", "🎫"),
    VOUCHER("바우처·예약확인서", "🧾"),
    CARD("카드·멤버십", "💳"),
    OTHER("그 밖에", "📄"),
    ;

    /** 만료일을 묻는 것이 말이 되는 종류인지. */
    val hasExpiry: Boolean get() = this == PASSPORT || this == VISA || this == INSURANCE || this == CARD

    companion object {
        fun fromName(name: String?): DocumentKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * 보관함에 넣어 둔 문서 한 건.
 *
 * 해외에서 데이터가 없을 때 꺼내 보는 것이 목적이므로 사진은 기기 안에 둔다. 번호처럼 민감한
 * 값은 목록에서 가려 두고 눌렀을 때만 보여 준다.
 */
data class TravelDocument(
    val id: Long = 0L,
    override val uid: String = "",
    override val updatedAt: Long = 0L,
    /** 이 여행에만 쓰는 문서인지. 여권처럼 여행과 무관한 것은 null. */
    val tripId: Long? = null,
    val kind: DocumentKind = DocumentKind.OTHER,
    val title: String,
    val number: String = "",
    val expiresOn: LocalDate? = null,
    val memo: String = "",
    /** 앱 저장소에 복사해 둔 사진. 없으면 번호만 적어 둔 문서다. */
    val filePath: String? = null,
    val createdAt: Long = 0L,
) : Syncable {

    val hasImage: Boolean get() = !filePath.isNullOrBlank()

    /** 목록에서 보여 줄 가린 번호. 뒤 네 자리만 남긴다. */
    val maskedNumber: String
        get() {
            val trimmed = number.trim()
            if (trimmed.length <= VISIBLE_TAIL) return trimmed
            return "•".repeat(trimmed.length - VISIBLE_TAIL) + trimmed.takeLast(VISIBLE_TAIL)
        }

    fun expired(today: LocalDate): Boolean = expiresOn?.isBefore(today) == true

    /** 곧 만료되는지. 여권은 여섯 달 전부터 챙겨야 한다. */
    fun expiresSoon(today: LocalDate, months: Long = DEFAULT_WARNING_MONTHS): Boolean {
        val expiry = expiresOn ?: return false
        return !expired(today) && expiry.isBefore(today.plusMonths(months))
    }

    fun daysUntilExpiry(today: LocalDate): Long? =
        expiresOn?.let { ChronoUnit.DAYS.between(today, it) }

    private companion object {
        const val VISIBLE_TAIL = 4
        const val DEFAULT_WARNING_MONTHS = 6L
    }
}

object TravelDocuments {

    /** 챙겨야 할 것이 위로. 만료가 가까운 것, 그다음이 종류 차례다. */
    fun sort(documents: List<TravelDocument>, today: LocalDate): List<TravelDocument> =
        documents.sortedWith(
            compareByDescending<TravelDocument> { it.expired(today) || it.expiresSoon(today) }
                .thenBy { it.expiresOn ?: LocalDate.MAX }
                .thenBy { it.kind.ordinal }
                .thenBy { it.title },
        )

    /** 곧 만료되거나 이미 만료된 것. 화면 위에 따로 보여 준다. */
    fun needsAttention(documents: List<TravelDocument>, today: LocalDate): List<TravelDocument> =
        documents.filter { it.expired(today) || it.expiresSoon(today) }

    /**
     * 이 여행에서 꺼내 볼 문서.
     *
     * 여행에 묶어 둔 것과 여행과 무관한 것(여권 같은)을 함께 본다. 다른 여행 것만 뺀다.
     */
    fun forTrip(documents: List<TravelDocument>, tripId: Long): List<TravelDocument> =
        documents.filter { it.tripId == null || it.tripId == tripId }
}
