package com.volp.travelbudget.domain.classify

import com.volp.travelbudget.domain.model.ExpenseCategory

/**
 * 가맹점 이름으로 지출 항목을 추측한다.
 *
 * 지금은 규칙 기반 구현 하나만 쓴다. 나중에 Gemini 같은 모델을 붙일 자리를 남겨 두려고
 * 인터페이스로 분리했다 — 구현만 갈아 끼우면 나머지 코드는 그대로다.
 */
fun interface MerchantClassifier {
    fun classify(merchant: String): ExpenseCategory?
}

/**
 * 해외 결제 문자의 가맹점명은 대부분 잘린 영문 대문자라(`OSAKAMUSEUMOFHISTORYAI`)
 * 부분 문자열로 찾는다.
 */
object RuleBasedMerchantClassifier : MerchantClassifier {

    private val rules: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.FLIGHT to listOf(
            "AIR", "항공", "KOREANAIR", "ASIANA", "JEJUAIR", "TWAY", "PEACH", "JETSTAR",
            "SKYSCANNER", "TRIPDOTCOM", "EXPEDIA",
        ),
        ExpenseCategory.LODGING to listOf(
            "HOTEL", "HOSTEL", "RYOKAN", "INN", "호텔", "AIRBNB", "AGODA", "BOOKING",
            "RESORT", "게스트하우스", "숙박", "야놀자", "여기어때",
        ),
        ExpenseCategory.TRANSPORT to listOf(
            "TAXI", "DIDI", "UBER", "GRAB", "RAIL", "METRO", "SUBWAY", "BUS", "KOTSU",
            "JR", "EXPRESS", "TRAIN", "카카오T", "택시", "지하철", "렌터카", "RENTACAR",
            "PARKING", "주차", "오일", "주유", "GS칼텍스", "SK에너지", "현대오일",
        ),
        ExpenseCategory.FOOD to listOf(
            "SEVENELEVEN", "FAMILYMART", "LAWSON", "MART", "SUSHI", "RAMEN", "CAFE",
            "COFFEE", "STARBUCKS", "스타벅스", "식당", "김밥", "치킨", "커피", "베이커리",
            "TSURUTONTAN", "RESTAURANT", "DINER", "IZAKAYA", "MCDONALD", "BURGER",
            "이마트", "쿠팡이츠", "배달의민족", "짬뽕", "맛집",
        ),
        ExpenseCategory.ACTIVITY to listOf(
            "MUSEUM", "PARK", "ZOO", "AQUARIUM", "TICKET", "TOUR", "TEMPLE", "SHRINE",
            "CASTLE", "관광", "입장", "박물관", "미술관", "테마파크", "KLOOK", "MYREALTRIP",
        ),
        ExpenseCategory.SHOPPING to listOf(
            "UNIQLO", "DONKI", "DONQUIJOTE", "MUJI", "LOFT", "BIC CAMERA", "BICCAMERA",
            "YODOBASHI", "MALL", "DUTYFREE", "면세", "백화점", "OUTLET", "NIKE", "ADIDAS",
            "ALPEN", "쇼핑", "올리브영", "다이소",
        ),
    )

    override fun classify(merchant: String): ExpenseCategory? {
        val normalized = merchant.uppercase().replace(" ", "")
        return rules.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(it.uppercase().replace(" ", "")) }
        }?.first
    }
}
