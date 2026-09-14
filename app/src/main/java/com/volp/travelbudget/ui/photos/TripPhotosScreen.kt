@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.volp.travelbudget.data.photos.TripPhoto
import com.volp.travelbudget.ui.common.volpViewModelFactory

/** 기기 버전에 따라 사진을 읽는 데 필요한 권한이 다르다. */
private val galleryPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun TripPhotosScreen(
    tripId: Long,
    onBack: () -> Unit,
) {
    val viewModel: TripPhotosViewModel = viewModel(
        key = "photos-$tripId",
        factory = volpViewModelFactory { app ->
            TripPhotosViewModel(
                repository = app.repository,
                photoStore = app.photoStore,
                tripId = tripId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, galleryPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (result) viewModel.loadDevicePhotos()
    }
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris ->
        viewModel.attach(uris)
    }

    LaunchedEffect(granted) {
        if (granted) viewModel.loadDevicePhotos()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.tripTitle} 사진") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "사진 추가")
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!granted) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "여행 기간에 찍은 사진을 자동으로 모으려면 사진 권한이 필요하다.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { permissionLauncher.launch(galleryPermission) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("사진 권한 허용") }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            if (state.loadingDevicePhotos) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            if (state.savedPhotos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("직접 넣은 사진 ${state.savedPhotos.size}장")
                }
                items(state.savedPhotos, key = { "saved-${it.id}" }) { photo ->
                    PhotoCell(photo = photo, onRemove = { viewModel.remove(photo.id) })
                }
            }

            if (state.devicePhotos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("여행 기간 기기 사진 ${state.devicePhotos.size}장")
                }
                items(state.devicePhotos, key = { "device-${it.id}" }) { photo ->
                    PhotoCell(photo = photo, onRemove = null)
                }
            }

            if (granted && state.devicePhotos.isEmpty() && state.savedPhotos.isEmpty() &&
                !state.loadingDevicePhotos
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "이 기간에 찍은 사진이 기기에 없다. 오른쪽 아래 버튼으로 직접 넣을 수 있다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun PhotoCell(photo: TripPhoto, onRemove: (() -> Unit)?) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "사진 빼기",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
