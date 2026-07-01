package com.iccyuan.hush.ui.editor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.iccyuan.hush.R
import com.iccyuan.hush.ui.theme.IOSColors

private val LOCATION_PERMS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** 从 Compose 的 context（在 Dialog 里可能是各种 ContextWrapper）一路解包出真正的 Activity。 */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun hasLocationPermission(c: Context): Boolean =
    LOCATION_PERMS.any { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

/**
 * 内嵌在条件弹窗里的高德地图选点。未授予定位权限时**不创建地图**（高德在无权限时初始化定位
 * 可能直接崩溃），改为显示授权入口；授权后再渲染地图。右下角自绘缩放/定位控件（毛玻璃）。
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
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }

    // 从系统权限弹窗返回时（ON_RESUME）重新检查授权状态，授予后即渲染地图。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = hasLocationPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // 打开时若未授权，自动弹一次系统权限申请（解包出 Activity 再申请，避免在 Dialog 里取不到）。
    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            context.findActivity()?.let { ActivityCompat.requestPermissions(it, LOCATION_PERMS, 0) }
        }
    }

    if (!granted) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.map_need_location),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(IOSColors.Blue)
                        .clickable {
                            context.findActivity()?.let { ActivityCompat.requestPermissions(it, LOCATION_PERMS, 0) }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.map_grant_location),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
        return
    }

    LocationMapContent(lat, lng, radiusMeters, onPick, modifier)
}

@Composable
private fun LocationMapContent(
    lat: Double,
    lng: Double,
    radiusMeters: Int,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    // 是否已经把镜头移到过当前位置（仅首次自动居中，避免反复抢镜头）。
    val centeredOnce = remember { booleanArrayOf(false) }
    // 用户是否已手动点图选点；以及打开时是否本就没有选点（新建条件）。
    val userPicked = remember { booleanArrayOf(false) }
    val initialUnset = remember { lat == 0.0 && lng == 0.0 }

    // 用 MapView（GL SurfaceView 版）：直接由系统合成，平移/缩放明显更流畅（不做逐帧纹理拷贝）。
    // 代价是它在独立 Surface 上渲染，Haze 无法对其做毛玻璃，故控件改用不透明白底、圆角也不作用于地图。
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
        // 首次定位成功时，若用户尚未选点，自动把镜头移到当前位置，并默认采用该位置——
        // 用户没在地图上选点时，就用定位到的当前位置作为围栏中心。
        map.setOnMyLocationChangeListener { loc ->
            if (!centeredOnce[0] && loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                centeredOnce[0] = true
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
                if (initialUnset && !userPicked[0]) onPick(loc.latitude, loc.longitude)
            }
        }
        map.setOnMapClickListener { p -> userPicked[0] = true; onPick(p.latitude, p.longitude) }
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
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        // 自绘控件：缩放（合并在一张卡上）+ 定位，靠右垂直排列。SurfaceView 上不能磨砂，改用不透明白底。
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Column(
                Modifier
                    .shadow(3.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(MapControlBg),
            ) {
                MapControl(Icons.Filled.Add) {
                    mapView.map.animateCamera(CameraUpdateFactory.zoomIn())
                }
                Box(Modifier.size(40.dp, 1.dp).background(Color(0x22000000)))
                MapControl(Icons.Filled.Remove) {
                    mapView.map.animateCamera(CameraUpdateFactory.zoomOut())
                }
            }
            Box(
                Modifier
                    .shadow(3.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(MapControlBg),
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

/** 地图控件背景：近乎不透明的白色（地图为浅色，白底控件与原生地图一致）。 */
private val MapControlBg = Color(0xF2FFFFFF)

@Composable
private fun MapControl(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF3C3C43), modifier = Modifier.size(22.dp))
    }
}
