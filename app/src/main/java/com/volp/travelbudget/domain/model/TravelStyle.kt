package com.volp.travelbudget.domain.model

/**
 * 여행 스타일. 같은 도시라도 어떻게 여행하느냐에 따라 경비가 크게 달라지므로
 * 하루 경비와 항공권에 각각 다른 배율을 적용한다.
 */
enum class TravelStyle(
    val label: String,
    val description: String,
    /** 숙박·식비 등 하루 단위 경비에 곱하는 배율. */
    val dailyMultiplier: Double,
    /** 항공권에 곱하는 배율. 좌석 등급 차이는 하루 경비만큼 벌어지지 않는다. */
    val flightMultiplier: Double,
) {
    BUDGET("알뜰", "게스트하우스, 현지 식당 위주", 0.65, 0.8),
    STANDARD("표준", "3~4성 호텔, 맛집 반 현지식 반", 1.0, 1.0),
    LUXURY("럭셔리", "고급 호텔, 파인다이닝 위주", 1.9, 1.6),
    ;

    companion object {
        fun fromName(name: String): TravelStyle =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
