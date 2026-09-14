package com.volp.travelbudget.domain.model

/**
 * 목적지 권역. 목록에 없는 도시를 직접 입력할 때 권역 평균 단가를 적용하는 데 쓴다.
 */
enum class Region(val label: String) {
    DOMESTIC("국내"),
    JAPAN("일본"),
    SOUTHEAST_ASIA("동남아"),
    GREATER_CHINA("중화권"),
    OCEANIA_PACIFIC("대양주·태평양"),
    EUROPE("유럽"),
    AMERICAS("미주"),
    ;

    companion object {
        fun fromName(name: String): Region =
            entries.firstOrNull { it.name == name } ?: SOUTHEAST_ASIA
    }
}
