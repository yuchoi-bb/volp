package com.volp.travelbudget.data.travel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.volp.travelbudget.domain.travel.GeoPoint
import kotlinx.coroutines.tasks.await

/**
 * 지금 어디에 있는지 알아낸다.
 *
 * 권한이 없거나 위치를 받지 못하면 null을 돌려준다. 위치는 있으면 좋은 정보일 뿐이라
 * 없다고 해서 일정 화면이 막히면 안 된다.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)

        return runCatching {
            // 마지막 위치가 있으면 바로 쓰고, 없을 때만 새로 잡는다. 배터리를 아끼기 위해서다.
            val last = client.lastLocation.await()
            val location = last ?: client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null,
            ).await()
            location?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
