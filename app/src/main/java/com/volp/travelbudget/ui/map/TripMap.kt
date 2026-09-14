package com.volp.travelbudget.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.domain.itinerary.PlanEntry
import com.volp.travelbudget.domain.travel.GeoPoint

/**
 * 그날 동선을 지도 위에 그린다.
 *
 * 지도 키가 없으면 빈 회색 화면 대신 지도 앱으로 넘기는 버튼을 보여 준다. 키가 없다고
 * 일정 화면 전체가 망가지면 안 된다.
 */
@Composable
fun TripMap(
    entries: List<PlanEntry>,
    current: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    val located = entries.mapNotNull { entry -> entry.point?.let { entry to it } }

    if (!BuildConfig.HAS_MAPS_KEY) {
        MapUnavailable(located.firstOrNull()?.second, modifier)
        return
    }
    if (located.isEmpty() && current == null) {
        MapEmpty(modifier)
        return
    }

    val points = located.map { LatLng(it.second.latitude, it.second.longitude) }
    val focus = points.firstOrNull()
        ?: current?.let { LatLng(it.latitude, it.longitude) }
        ?: return

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(focus, 13f)
    }

    // 장소가 여러 곳이면 전부 들어오도록 화면을 맞춘다.
    LaunchedEffect(points, current) {
        val all = points + listOfNotNull(current?.let { LatLng(it.latitude, it.longitude) })
        if (all.size < 2) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(all.firstOrNull() ?: focus, 14f))
            return@LaunchedEffect
        }
        val bounds = LatLngBounds.builder().apply { all.forEach(::include) }.build()
        runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120)) }
    }

    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        ) {
            if (points.size >= 2) {
                Polyline(
                    points = points,
                    color = MaterialTheme.colorScheme.primary,
                    width = 10f,
                )
            }

            located.forEachIndexed { index, (entry, point) ->
                Marker(
                    state = rememberMarkerState(
                        key = "stop-${entry.title}-$index",
                        position = LatLng(point.latitude, point.longitude),
                    ),
                    title = "${index + 1}. ${entry.title}",
                    snippet = entry.time?.toString().orEmpty(),
                )
            }

            current?.let { here ->
                Marker(
                    state = rememberMarkerState(
                        key = "current",
                        position = LatLng(here.latitude, here.longitude),
                    ),
                    title = "지금 여기",
                )
            }
        }
    }
}

@Composable
private fun MapUnavailable(first: GeoPoint?, modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "지도 키가 설정되지 않아 지도를 띄울 수 없다.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "빌드에 MAPS_API_KEY를 넣으면 여기에 동선이 그려진다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (first != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:${first.latitude},${first.longitude}?z=14"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) { Text("지도 앱으로 열기") }
            }
        }
    }
}

@Composable
private fun MapEmpty(modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                "장소를 넣으면 여기에 동선이 그려진다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
