package com.volp.travelbudget.domain.model

/**
 * 목적지 한 곳의 기준 단가표. 모든 금액 단위는 원(KRW)이며 [TravelStyle.STANDARD] 기준이다.
 *
 * @param flightPerPerson 1인 왕복 항공권
 * @param lodgingPerNight 객실 1개 1박 (성인 2명이 객실 하나를 쓰는 것으로 계산한다)
 * @param foodPerDay 1인 하루 식비
 * @param transportPerDay 1인 하루 현지 교통비
 * @param activityPerDay 1인 하루 관광·액티비티 비용
 * @param shoppingPerDay 1인 하루 쇼핑 비용
 */
data class Destination(
    val key: String,
    val name: String,
    val region: Region,
    val currencyCode: String,
    val flightPerPerson: Long,
    val lodgingPerNight: Long,
    val foodPerDay: Long,
    val transportPerDay: Long,
    val activityPerDay: Long,
    val shoppingPerDay: Long,
)
