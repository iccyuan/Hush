package com.iccyuan.hush.ui.editor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.amap.api.maps.model.MyLocationStyle

/**
 * 内嵌在条件弹窗里的高德地图选点：开启「我的位置」蓝点，打开时自动定位到当前位置；
 * 右下角是自绘的放大/缩小 + 定位控件（替代高德默认控件，统一风格）。点地图落点回调 [onPick]。
 */
@Composable
fun LocationMap(
    lat: Double,
    lng: Double,
    radiusMeters: Int,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 是否已经把镜头移到过当前位置（仅首次自动居中，避免反复抢镜头）。
    val centeredOnce = remember { booleanArrayOf(false) }

    // 直接经 Activity 申请定位权限——Compose 的 rememberLauncherForActivityResult 在 Dialog
    // 子组合里取不到 ActivityResultRegistryOwner 会崩溃。
    LaunchedEffect(Unit) {
        val needed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            (context as? Activity)?.let { ActivityCompat.requestPermissions(it, needed.toTypedArray(), 0) }
        }
    }

    // 高德要求在构造 MapView 之前完成隐私合规声明，否则会抛异常/崩溃。
    val mapView = remember {
        runCatching {
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
        }
        MapView(context)
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
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false // 用自绘控件替代
        // 我的位置蓝点（不自动居中，由下方监听首次定位手动居中）。
        map.myLocationStyle = MyLocationStyle()
            .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            .interval(2000)
        map.isMyLocationEnabled = true

        if (lat != 0.0 || lng != 0.0) {
            centeredOnce[0] = true
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
        }
        // 首次定位成功时，若用户尚未选点，自动把镜头移到当前位置。
        map.setOnMyLocationChangeListener { loc ->
            if (!centeredOnce[0] && loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                centeredOnce[0] = true
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
            }
        }
        map.setOnMapClickListener { p -> onPick(p.latitude, p.longitude) }
    }

    // 选点/半径变化时重绘标记 + 半径圈。
    LaunchedEffect(lat, lng, radiusMeters) {
        val map = mapView.map
        map.clear(true)
        if (lat != 0.0 || lng != 0.0) {
            val point = LatLng(lat, lng)
            map.addMarker(MarkerOptions().position(point))
            map.addCircle(
                CircleOptions().center(point).radius(radiusMeters.toDouble())
                    .strokeWidth(3f).strokeColor(0xFFFF3B30.toInt()).fillColor(0x33FF3B30)
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // 自绘控件：缩放（合并在一张白卡上）+ 定位，靠右垂直排列。
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Column(
                Modifier
                    .shadow(3.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            ) {
                MapControl(Icons.Filled.Add) {
                    mapView.map.animateCamera(CameraUpdateFactory.zoomIn())
                }
                Box(Modifier.size(40.dp, 1.dp).background(Color(0x14000000)))
                MapControl(Icons.Filled.Remove) {
                    mapView.map.animateCamera(CameraUpdateFactory.zoomOut())
                }
            }
            Box(
                Modifier
                    .shadow(3.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            ) {
                MapControl(Icons.Filled.MyLocation) {
                    mapView.map.myLocation?.let { loc ->
                        mapView.map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapControl(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF3C3C43), modifier = Modifier.size(22.dp))
    }
}
