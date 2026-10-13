package com.atouboot.radiusmap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.atouboot.radiusmap.data.AppPrefs
import com.atouboot.radiusmap.databinding.ActivityMainBinding
import com.atouboot.radiusmap.util.GeoUtils
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.Circle
import com.baidu.mapapi.map.CircleOptions
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.map.Stroke
import com.baidu.mapapi.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * 主界面：地图选点 + 圆形半径定位。
 *
 * MVP 能力：
 *  - 点击地图任意位置设为圆心
 *  - 读取手机当前 GPS 位置，可一键设为圆心
 *  - 输入半径（km，精确到 0.01），实时绘制半透明圆形区域
 *  - 显示圆心经纬度、定位精度、覆盖面积
 *  - 运行时可配置百度地图 AK（不写入仓库）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var baiduMap: BaiduMap

    /** 当前圆心 */
    private var centerLatLng: LatLng? = null

    /** 当前圆形覆盖物 */
    private var circle: Circle? = null

    /** 圆心标记 */
    private var centerMarker: Marker? = null

    /** 当前半径（km） */
    private var radiusKm: Double = DEFAULT_RADIUS_KM

    /** 最近一次定位精度（米） */
    private var accuracyMeters: Float? = null

    /** 最近一次定位坐标 */
    private var lastLocation: LatLng? = null

    private var locationClient: LocationClient? = null
    private var pendingAction: Int = ACTION_NONE

    private val centerIcon: BitmapDescriptor by lazy { buildCenterIcon() }

    // ------------------------------------------------------------------ 生命周期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initBaiduSdk()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupLocationClient()
        setupRadiusInput()
        setupButtons()

        updateInfo()

        // 首次启动且没有可用 AK 时，引导用户配置
        if (effectiveAk.isEmpty()) {
            showAkDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        locationClient?.let { client ->
            client.stop()
            client.unRegisterLocationListener(locationListener)
        }
        locationClient = null

        if (::binding.isInitialized) {
            (binding.mapView.parent as? ViewGroup)?.removeView(binding.mapView)
            binding.mapView.onDestroy()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 百度 SDK 初始化

    /**
     * 解析 AK：优先使用 App 内填写的，其次使用编译期注入的。
     */
    private var effectiveAk: String = ""

    private fun resolveAk(): String {
        val fromPrefs = AppPrefs.getBaiduAk()
        if (fromPrefs.isNotEmpty()) return fromPrefs
        return readManifestAk()
    }

    @Suppress("DEPRECATION")
    private fun readManifestAk(): String = try {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString(META_KEY_AK)?.trim().orEmpty()
    } catch (t: Throwable) {
        ""
    }

    private fun initBaiduSdk() {
        effectiveAk = resolveAk()
        // 合规声明：本应用为自用工具，调用方已同意百度定位/地图 SDK 的隐私政策
        SDKInitializer.setAgreePrivacy(this, true)
        if (effectiveAk.isNotEmpty()) {
            SDKInitializer.setApiKey(effectiveAk)
        }
        SDKInitializer.initialize(this)
    }

    // ------------------------------------------------------------------ 地图

    private fun setupMap() {
        val mapView: MapView = binding.mapView
        mapView.showZoomControls(false)

        baiduMap = mapView.getMap()
        baiduMap.setMyLocationEnabled(true)
        baiduMap.setMyLocationConfiguration(
            MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, false, null)
        )
        baiduMap.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(latLng: LatLng?) {
                // 点击地图选点：不移动镜头，避免与用户操作冲突
                if (latLng != null) setCenter(latLng, moveCamera = false)
            }

            override fun onMapPoiClick(mapPoi: MapPoi?) {
                // MVP 不处理 POI 点击
            }
        })
    }

    private fun setCenter(latLng: LatLng, moveCamera: Boolean) {
        centerLatLng = latLng
        if (moveCamera) {
            baiduMap.animateMapStatus(
                MapStatusUpdateFactory.newLatLngZoom(latLng, zoomForRadius(radiusKm))
            )
        }
        applyCircle()
    }

    private fun moveCamera(latLng: LatLng) {
        baiduMap.animateMapStatus(
            MapStatusUpdateFactory.newLatLngZoom(latLng, zoomForRadius(radiusKm))
        )
    }

    /**
     * 根据半径选择合适的缩放级别，让圆形大致铺满屏幕。
     */
    private fun zoomForRadius(km: Double): Float = when {
        km <= 0.1 -> 16f
        km <= 0.3 -> 15f
        km <= 0.5 -> 14.5f
        km <= 1.0 -> 14f
        km <= 2.0 -> 13f
        km <= 5.0 -> 12f
        km <= 10.0 -> 11f
        km <= 50.0 -> 9f
        km <= 200.0 -> 7f
        km <= 1000.0 -> 5f
        else -> 3f
    }

    /**
     * 绘制 / 更新圆形区域与圆心标记。
     */
    private fun applyCircle() {
        val center = centerLatLng
        if (center == null) {
            circle?.remove()
            circle = null
            centerMarker?.remove()
            centerMarker = null
            updateInfo()
            return
        }

        val radiusMeters = (radiusKm * 1000.0).toInt().coerceAtLeast(1)

        val existingCircle = circle
        if (existingCircle != null) {
            existingCircle.setCenter(center)
            existingCircle.setRadius(radiusMeters)
        } else {
            val options = CircleOptions()
                .center(center)
                .radius(radiusMeters)
                .fillColor(FILL_COLOR)
                .stroke(Stroke(STROKE_WIDTH_PX, STROKE_COLOR))
            circle = baiduMap.addOverlay(options) as? Circle
        }

        val existingMarker = centerMarker
        if (existingMarker != null) {
            existingMarker.setPosition(center)
        } else {
            val markerOptions = MarkerOptions()
                .position(center)
                .icon(centerIcon)
                .anchor(0.5f, 0.5f)
            centerMarker = baiduMap.addOverlay(markerOptions) as? Marker
        }

        updateInfo()
    }

    private fun clearCircle() {
        circle?.remove()
        circle = null
        centerMarker?.remove()
        centerMarker = null
        centerLatLng = null
        updateInfo()
    }

    private fun buildCenterIcon(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (20 * density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.density = resources.displayMetrics.densityDpi

        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(r, r, r, paint)

        paint.color = STROKE_COLOR
        canvas.drawCircle(r, r, r * 0.72f, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(r, r, r * 0.24f, paint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    // ------------------------------------------------------------------ 定位

    private fun setupLocationClient() {
        val option = LocationClientOption()
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy)
        option.setCoorType("bd09ll")
        option.setScanSpan(2000)
        option.setIsNeedAddress(false)
        option.setIsNeedLocationDescribe(false)
        option.setOpenGps(true)
        option.setIgnoreKillProcess(true)
        option.setFirstLocType(LocationClientOption.FirstLocType.ACCURACY_IN_FIRST_LOC)

        val client = LocationClient(this)
        client.setLocOption(option)
        client.registerLocationListener(locationListener)
        locationClient = client
    }

    private val locationListener = object : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation?) {
            if (location == null) return

            val locType = location.getLocType()
            val ok = locType == BDLocation.TypeGpsLocation ||
                locType == BDLocation.TypeGnssLocation ||
                locType == BDLocation.TypeNetWorkLocation ||
                locType == BDLocation.TypeOffLineLocation ||
                locType == BDLocation.TypeCacheLocation ||
                locType == BDLocation.TypeCoarseLocation

            if (!ok) {
                runOnUiThread { toast(getString(R.string.toast_location_failed)) }
                return
            }

            val lat = location.getLatitude()
            val lng = location.getLongitude()
            val accuracy = location.getRadius()
            val fix = LatLng(lat, lng)

            runOnUiThread {
                lastLocation = fix
                accuracyMeters = accuracy
                updateMyLocationDot(fix, accuracy)
                updateInfo()
                if (pendingAction != ACTION_NONE) {
                    val action = pendingAction
                    pendingAction = ACTION_NONE
                    when (action) {
                        ACTION_MOVE_CAMERA -> moveCamera(fix)
                        ACTION_SET_CENTER -> setCenter(fix, moveCamera = true)
                    }
                }
            }
        }
    }

    private fun updateMyLocationDot(latLng: LatLng, accuracy: Float) {
        baiduMap.setMyLocationEnabled(true)
        val data = MyLocationData.Builder()
            .latitude(latLng.latitude)
            .longitude(latLng.longitude)
            .accuracy(accuracy)
            .build()
        baiduMap.setMyLocationData(data)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            toast(getString(R.string.toast_locating))
            startLocate()
        } else {
            pendingAction = ACTION_NONE
            toast(getString(R.string.toast_no_permission))
        }
    }

    private fun startLocate() {
        val client = locationClient ?: return
        if (!client.isStarted()) {
            client.start()
        }
        client.requestLocation()
    }

    /**
     * 确保有定位权限，然后在拿到定位后执行 [action]。
     */
    private fun ensureLocationThen(action: Int) {
        pendingAction = action

        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val known = lastLocation
        if (known != null) {
            pendingAction = ACTION_NONE
            when (action) {
                ACTION_MOVE_CAMERA -> moveCamera(known)
                ACTION_SET_CENTER -> setCenter(known, moveCamera = true)
            }
            // 同时刷新一次，更新精度显示
            startLocate()
        } else {
            toast(getString(R.string.toast_locating))
            startLocate()
        }
    }

    // ------------------------------------------------------------------ 半径输入

    private fun setupRadiusInput() {
        binding.etRadius.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim().orEmpty()
                if (text.isEmpty()) {
                    updateInfo()
                    return
                }
                val value = text.toDoubleOrNull()
                if (value == null || value <= 0.0) {
                    updateInfo()
                    return
                }
                radiusKm = value.coerceAtMost(MAX_RADIUS_KM)
                applyCircle()
            }
        })
    }

    // ------------------------------------------------------------------ 按钮

    private fun setupButtons() {
        binding.btnLocate.setOnClickListener {
            ensureLocationThen(ACTION_MOVE_CAMERA)
        }

        binding.btnUseLocationAsCenter.setOnClickListener {
            ensureLocationThen(ACTION_SET_CENTER)
        }

        binding.btnClear.setOnClickListener {
            clearCircle()
        }

        binding.btnSettings.setOnClickListener {
            showAkDialog()
        }
    }

    // ------------------------------------------------------------------ 信息展示

    private fun updateInfo() {
        val center = centerLatLng
        binding.tvCenter.text = getString(
            R.string.fmt_center,
            if (center == null) getString(R.string.value_placeholder)
            else GeoUtils.formatLatLng(center.latitude, center.longitude)
        )

        val accuracy = accuracyMeters
        binding.tvAccuracy.text = getString(
            R.string.fmt_accuracy,
            if (accuracy == null || accuracy <= 0f) getString(R.string.value_placeholder)
            else getString(R.string.accuracy_meters, String.format(Locale.US, "%.1f", accuracy))
        )

        binding.tvRadius.text = getString(
            R.string.fmt_radius_area,
            GeoUtils.formatKm(radiusKm),
            GeoUtils.formatKm(GeoUtils.areaKm2(radiusKm))
        )
    }

    // ------------------------------------------------------------------ AK 配置

    private fun showAkDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.dialog_ak_hint)
        input.setText(AppPrefs.getBaiduAk())
        input.setSingleLine(true)

        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val container = FrameLayout(this)
        container.setPadding(padding, padding / 2, padding, 0)
        container.addView(
            input,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_ak_title)
            .setMessage(R.string.dialog_ak_message)
            .setView(container)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val ak = input.text?.toString()?.trim().orEmpty()
                if (ak.isEmpty()) {
                    toast(getString(R.string.toast_ak_empty))
                } else {
                    AppPrefs.setBaiduAk(ak)
                    toast(getString(R.string.toast_ak_saved))
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ 工具

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val META_KEY_AK = "com.baidu.lbsapi.API_KEY"

        private const val DEFAULT_RADIUS_KM = 1.0
        private const val MAX_RADIUS_KM = 10000.0

        private const val ACTION_NONE = 0
        private const val ACTION_MOVE_CAMERA = 1
        private const val ACTION_SET_CENTER = 2

        /** 圆形填充色：约 30% 透明度的蓝色 */
        private val FILL_COLOR = Color.parseColor("#4D2196F3")

        /** 圆形描边色 */
        private val STROKE_COLOR = Color.parseColor("#CC1976D2")

        private const val STROKE_WIDTH_PX = 4
    }
}
