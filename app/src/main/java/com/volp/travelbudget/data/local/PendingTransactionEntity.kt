package com.volp.travelbudget.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.volp.travelbudget.domain.cardsms.CardIssuer
import com.volp.travelbudget.domain.cardsms.CardTransaction
import com.volp.travelbudget.domain.cardsms.TransactionKind
import java.time.LocalDateTime

/** 결제 내역이 어디서 들어왔는지. */
enum class CaptureSource(val label: String) {
    SMS("문자"),
    NOTIFICATION("앱 알림"),
}

/** 미확인함에서의 처리 상태. */
enum class PendingStatus {
    /** 아직 여행에 넣지 않은 상태. */
    PENDING,

    /** 여행 지출로 옮긴 상태. */
    ACCEPTED,

    /** 여행과 무관해 넘긴 상태. */
    IGNORED,
}

/**
 * 카드 문자·알림에서 읽어 들인 결제 한 건.
 *
 * 곧바로 지출로 만들지 않고 여기에 쌓아 두었다가, 사용자가 어느 여행의 지출인지 정하면
 * 그때 [ExpenseEntity]로 옮긴다. 여행과 상관없는 생활비가 섞여 들어오기 때문이다.
 */
@Entity(
    tableName = "pending_transactions",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["status"]),
    ],
)
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val issuer: String,
    val cardLabel: String,
    val holderName: String?,
    val kind: String,
    val amount: Double,
    val currencyCode: String,
    val countryCode: String?,
    val merchant: String,
    val occurredAt: LocalDateTime,
    val paymentPlan: String?,
    val source: String,
    val receivedAt: Long,
    val status: String,
    /** 내 명의가 아닌 결제인지. 설정한 이름과 다르면 true가 되어 '미분류'로 모인다. */
    val foreignHolder: Boolean,
    val tripId: Long?,
    val expenseId: Long?,
    val rawBody: String,
    /**
     * 같은 결제가 문자와 앱 알림으로 두 번 들어오는 것을 막는 열쇠.
     * 가맹점 표기는 경로마다 달라서 빼고, 카드·금액·시각만으로 만든다.
     */
    val fingerprint: String,
)

fun CardTransaction.toPendingEntity(
    source: CaptureSource,
    ownerName: String,
    receivedAt: Long = System.currentTimeMillis(),
): PendingTransactionEntity = PendingTransactionEntity(
    issuer = issuer.name,
    cardLabel = cardLabel,
    holderName = holderName,
    kind = kind.name,
    amount = amount,
    currencyCode = currencyCode,
    countryCode = countryCode,
    merchant = merchant,
    occurredAt = occurredAt,
    paymentPlan = paymentPlan,
    source = source.name,
    receivedAt = receivedAt,
    status = PendingStatus.PENDING.name,
    foreignHolder = ownerName.isNotBlank() &&
        !holderName.isNullOrBlank() &&
        holderName != ownerName,
    tripId = null,
    expenseId = null,
    rawBody = rawBody,
    fingerprint = fingerprintOf(),
)

private fun CardTransaction.fingerprintOf(): String =
    listOf(
        issuer.name,
        cardLabel,
        kind.name,
        currencyCode,
        // 소수점 둘째 자리까지만 본다. 같은 결제가 경로에 따라 미세하게 다르게 오지는 않는다.
        String.format(java.util.Locale.US, "%.2f", amount),
        occurredAt.withSecond(0).withNano(0).toString(),
    ).joinToString("|")

/** 화면에서 쓰기 좋게 풀어 둔 형태. */
data class PendingTransaction(
    val id: Long,
    val issuer: CardIssuer,
    val cardLabel: String,
    val holderName: String?,
    val kind: TransactionKind,
    val amount: Double,
    val currencyCode: String,
    val countryCode: String?,
    val merchant: String,
    val occurredAt: LocalDateTime,
    val source: CaptureSource,
    val foreignHolder: Boolean,
    val rawBody: String,
) {
    val isOverseas: Boolean get() = currencyCode != "KRW"
    val signedAmount: Double get() = if (kind == TransactionKind.CANCEL) -amount else amount
}

fun PendingTransactionEntity.toDomain(): PendingTransaction = PendingTransaction(
    id = id,
    issuer = runCatching { CardIssuer.valueOf(issuer) }.getOrDefault(CardIssuer.SAMSUNG),
    cardLabel = cardLabel,
    holderName = holderName,
    kind = runCatching { TransactionKind.valueOf(kind) }.getOrDefault(TransactionKind.APPROVAL),
    amount = amount,
    currencyCode = currencyCode,
    countryCode = countryCode,
    merchant = merchant,
    occurredAt = occurredAt,
    source = runCatching { CaptureSource.valueOf(source) }.getOrDefault(CaptureSource.SMS),
    foreignHolder = foreignHolder,
    rawBody = rawBody,
)
