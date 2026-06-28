package com.iccyuan.hush.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.iccyuan.hush.R

/**
 * 高德地图选点页：点地图落点，确定后回传经纬度 + 显示名（坐标）。
 * MapView 的生命周期需手动转发（onCreate/onResume/onPause/onDestroy）。
 */
@Composable
fun MapPickerScreen(
    initialLat: Double,
    initialLng: Double,
    radiusMeters: Int = 300,
    onCancel: () -> Unit,
    onConfirm: (Double, Double, String) -> Unit,
) {
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        // 高德隐私合规：展示并同意后才能加载地图。
        runCatching {
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
        }
        // 定位权限（地图定位 + 围栏需要；后台围栏还需在设置里授予「始终允许」）。
        val needed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    val mapView = remember { MapView(context) }
    var selected by remember {
        mutableStateOf(
            if (initialLat != 0.0 || initialLng != 0.0) LatLng(initialLat, initialLng) else null
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        val map = mapView.map
        selected?.let { map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
        map.setOnMapClickListener { latLng -> selected = latLng }
    }

    // 落点变化时重绘标记 + 半径圈。
    LaunchedEffect(selected, radiusMeters) {
        val map = mapView.map
        map.clear()
        selected?.let {
            map.addMarker(MarkerOptions().position(it))
            map.addCircle(
                CircleOptions().center(it).radius(radiusMeters.toDouble())
                    .strokeWidth(3f).strokeColor(0xFFFF3B30.toInt()).fillColor(0x33FF3B30)
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResourceCancel()) }
            Text(stringResourcePickTitle(), style = MaterialTheme.typography.titleMedium)
            TextButton(
                enabled = selected != null,
                onClick = {
                    selected?.let { onConfirm(it.latitude, it.longitude, "%.5f, %.5f".format(it.latitude, it.longitude)) }
                },
            ) { Text(stringResourceDone()) }
        }
    }
}

@Composable private fun stringResourceCancel() = androidx.compose.ui.res.stringResource(R.string.cancel)
@Composable private fun stringResourceDone() = androidx.compose.ui.res.stringResource(R.string.done)
@Composable private fun stringResourcePickTitle() = androidx.compose.ui.res.stringResource(R.string.location_pick_title)
