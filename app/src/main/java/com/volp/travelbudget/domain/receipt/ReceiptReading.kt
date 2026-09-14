package com.volp.travelbudget.domain.receipt

import java.time.LocalDate

/**
 * 영수증 사진에서 읽어 낸 값.
 *
 * 모르는 자리는 비워 둔다. 지어내는 것보다 비워 두는 편이 낫다. 사람이 사진과 견주어 채운다.
 */
data class ReceiptReading(
    val merchant: String = "",
    val total: Double? = null,
    val currencyCode: String = "",
    val date: LocalDate? = null,
    /** 읽은 사람이 얼마나 확신하는지(0~1). 흐린 사진이면 낮다. */
    val confidence: Double = 0.0,
) {
    val isUsable: Boolean get() = total != null && total > 0.0

    /** 사람에게 한 줄로 알려 줄 말. */
    val summary: String
        get() = listOfNotNull(
            merchant.takeIf { it.isNotBlank() },
            total?.let { amount -> "$amount ${currencyCode.ifBlank { "" }}".trim() },
            date?.toString(),
        ).joinToString(" · ").ifBlank { "읽어 낸 것이 없다" }
}

/**
 * 영수증을 읽어 오는 곳.
 *
 * 무엇으로 읽는지는 이 자리에서 감춘다. 지금은 Gemini를 쓰지만, 기기 안에서 읽는 방식으로
 * 바꾸더라도 부르는 쪽은 그대로다.
 */
fun interface ReceiptReader {

    /**
     * @param image 영수증 사진의 바이트
     * @return 읽어 낸 값. 읽지 못했으면 null.
     */
    suspend fun read(image: ByteArray, mimeType: String): ReceiptReading?
}

/** 답으로 온 JSON을 [ReceiptReading]으로 바꾸는 규칙. 어떤 모델을 쓰든 같은 모양을 요구한다. */
object ReceiptParsing {

    /** 모델에게 이 모양으로만 답하라고 시킨다. 설명이 섞이면 읽을 수 없다. */
    const val INSTRUCTION: String =
        "이 영수증 사진에서 가맹점 이름, 총 결제 금액, 통화 코드, 날짜를 찾아 JSON만 답해라. " +
            "형식: {\"merchant\":\"\",\"total\":0,\"currency\":\"KRW\",\"date\":\"2026-01-31\",\"confidence\":0.0}. " +
            "모르는 값은 빈 문자열이나 null로 두고, 설명이나 코드 블록 없이 JSON만 출력해라."

    /**
     * 답에서 JSON 부분만 떼어 낸다.
     *
     * 모델이 코드 블록으로 감싸거나 앞뒤에 말을 붙이는 일이 흔해, 중괄호 사이만 잘라 쓴다.
     */
    fun extractJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value.trim()) }.getOrNull()
    }

    /** 금액에 통화 기호나 쉼표가 섞여 와도 읽는다. */
    fun parseAmount(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.filter { it.isDigit() || it == '.' || it == '-' }
        return cleaned.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    fun normalizeCurrency(value: String?): String {
        val trimmed = value?.trim()?.uppercase().orEmpty()
        return if (trimmed.length == CURRENCY_LENGTH && trimmed.all { it.isLetter() }) trimmed else ""
    }

    private const val CURRENCY_LENGTH = 3
}
