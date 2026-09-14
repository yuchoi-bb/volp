package com.volp.travelbudget.data.travel

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.volp.travelbudget.domain.travel.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** 장소 이름으로 찾은 결과. */
data class PlaceResult(
    val name: String,
    val address: String,
    val point: GeoPoint,
)

/**
 * 장소 이름을 좌표로 바꾼다.
 *
 * 안드로이드에 들어 있는 지오코더를 쓴다. 지도 API 키가 필요 없고, 찾지 못해도 이름만으로
 * 일정에 넣을 수 있게 해 두어 기록이 막히지 않는다.
 */
class PlaceLookup(private val context: Context) {

    /**
     * @param near 있으면 그 근처를 먼저 찾는다. 같은 이름의 가게가 세계 곳곳에 있기 때문이다.
     */
    suspend fun find(query: String, near: GeoPoint? = null): PlaceResult? {
        if (query.isBlank() || !Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.KOREA)

        val nearby = near?.let { lookup(geocoder, query, it) }
        return nearby ?: lookup(geocoder, query, null)
    }

    private suspend fun lookup(
        geocoder: Geocoder,
        query: String,
        near: GeoPoint?,
    ): PlaceResult? = runCatching {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            awaitAddresses(geocoder, query, near)
        } else {
            withContext(Dispatchers.IO) { legacyAddresses(geocoder, query, near) }
        }
        addresses.firstOrNull()?.toResult(query)
    }.getOrNull()

    private suspend fun awaitAddresses(
        geocoder: Geocoder,
        query: String,
        near: GeoPoint?,
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }

        if (near == null) {
            geocoder.getFromLocationName(query, MAX_RESULTS, listener)
        } else {
            geocoder.getFromLocationName(
                query,
                MAX_RESULTS,
                near.latitude - SEARCH_SPAN,
                near.longitude - SEARCH_SPAN,
                near.latitude + SEARCH_SPAN,
                near.longitude + SEARCH_SPAN,
                listener,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyAddresses(
        geocoder: Geocoder,
        query: String,
        near: GeoPoint?,
    ): List<Address> = if (near == null) {
        geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty()
    } else {
        geocoder.getFromLocationName(
            query,
            MAX_RESULTS,
            near.latitude - SEARCH_SPAN,
            near.longitude - SEARCH_SPAN,
            near.latitude + SEARCH_SPAN,
            near.longitude + SEARCH_SPAN,
        ).orEmpty()
    }

    private fun Address.toResult(query: String): PlaceResult {
        val label = (0..maxAddressLineIndex)
            .mapNotNull { getAddressLine(it) }
            .joinToString(" ")
            .ifBlank { listOfNotNull(countryName, locality, thoroughfare).joinToString(" ") }

        return PlaceResult(
            name = featureName?.takeIf { it.isNotBlank() && it != query } ?: query,
            address = label,
            point = GeoPoint(latitude, longitude),
        )
    }

    private companion object {
        const val MAX_RESULTS = 1

        /** 도시 하나를 덮을 정도의 검색 범위(도 단위). */
        const val SEARCH_SPAN = 0.7
    }
}
