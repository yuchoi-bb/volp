package com.volp.travelbudget.domain.budget

/**
 * 통화별 기본 환율(1단위당 원). 실시간 시세가 아니라 입력을 도와주는 초기값이며,
 * 여행을 만들 때 사용자가 직접 고칠 수 있다.
 */
object CurrencyRates {

    data class Currency(
        val code: String,
        val label: String,
        val krwPerUnit: Double,
    )

    val currencies: List<Currency> = listOf(
        Currency("KRW", "원", 1.0),
        Currency("JPY", "엔", 9.3),
        Currency("USD", "달러", 1_380.0),
        Currency("EUR", "유로", 1_500.0),
        Currency("GBP", "파운드", 1_760.0),
        Currency("CNY", "위안", 190.0),
        Currency("TWD", "대만달러", 43.0),
        Currency("HKD", "홍콩달러", 178.0),
        Currency("SGD", "싱가포르달러", 1_030.0),
        Currency("THB", "바트", 38.0),
        Currency("VND", "동", 0.055),
        Currency("IDR", "루피아", 0.085),
        Currency("PHP", "페소", 24.0),
        Currency("AUD", "호주달러", 900.0),
        Currency("CAD", "캐나다달러", 1_010.0),
        Currency("CZK", "코루나", 60.0),
    )

    private val byCode = currencies.associateBy { it.code }

    fun defaultRate(code: String): Double = byCode[code]?.krwPerUnit ?: 1.0

    fun label(code: String): String = byCode[code]?.label ?: code

    fun isKrw(code: String): Boolean = code == "KRW"
}
