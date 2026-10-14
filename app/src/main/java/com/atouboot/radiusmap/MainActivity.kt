package com.atouboot.radiusmap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.atouboot.radiusmap.data.AppPrefs
import com.atouboot.radiusmap.data.CircleStore
import com.atouboot.radiusmap.data.CircleZone
import com.atouboot.radiusmap.databinding.ActivityMainBinding
import com.atouboot.radiusmap.ui.CircleZoneAdapter
import com.atouboot.radiusmap.ui.ZoneColors
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
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.Stroke
import com.baidu.mapapi.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * 主界面：地图选点 + 圆形半径定位。
 *
 * 能力：
 *  - 点击地图任意位置设为圆心
 *  - 读取手机当前 GPS 位置，可一键设为圆心
 *  - 输入半径（km，精确到 0.01），实时绘制半透明圆形区域
 *  - 显示圆心经纬度、定位精度、覆盖面积
 *  - 保存 / 管理 / 清除多个圆形区域（本地持久化）
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

    /** 已保存的区域列表 */
    private lateinit var zoneAdapter: CircleZoneAdapter
    private val zones = mutableListOf<CircleZone>()

    /** 已保存区域在地图上的覆盖物：zoneId -> [圆形, 圆心点, 名称标签] */
    private val zoneOverlays = mutableMapOf<Long, MutableList<Overlay>>()

    private val centerIcon: BitmapDescriptor by lazy { buildCenterIcon() }

    /** 解析后的 AK（App 内填写优先，其次编译期注入） */
    private var effectiveAk: String = ""

    // ------------------------------------------------------------------ 生命周期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initBaiduSdk()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupLocationClient()
        setupRadiusInput()
        setupZoneList()
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
        // 注意 1：百度 SDK 的 setAgreePrivacy / initialize 要求传入 ApplicationContext，
        //         传 Activity 会抛 BDMapSDKException: context must be an ApplicationContext。
        // 注意 2：定位 SDK 与地图 SDK 的隐私开关是【两个独立】的，
        //         只调 SDKInitializer 会让 LocationClient 构造时抛
        //         "The location function has been stopped because you do not agree with the privacy compliance policy"。
        val appContext = applicationContext
        // 合规声明：本应用为自用工具，调用方已同意百度定位/地图 SDK 的隐私政策
        SDKInitializer.setAgreePrivacy(appContext, true)
        LocationClient.setAgreePrivacy(true)

        if (effectiveAk.isNotEmpty()) {
            // 地图 SDK 的 AK
            SDKInitializer.setApiKey(effectiveAk)
            // 注意 3：定位 SDK 的 AK 需要单独设置（LocationClient.setKey），
            //         只调 SDKInitializer.setApiKey 定位仍会报 errorcode 101。
            LocationClient.setKey(effectiveAk)
        }
        SDKInitializer.initialize(appContext)
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
                // 不处理 POI 点击
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

    /**
     * 只把镜头平移到目标点，**不改变缩放级别**。
     * 用于「定位到我的位置」：需要移动视角，但不该顺带改变用户当前的缩放。
     */
    private fun moveCamera(latLng: LatLng) {
        baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLng(latLng))
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
                // 草稿圆用虚线描边，与「已保存区域」的实线圆区分开
                .dottedStroke(true)
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

        val client = LocationClient(applicationContext)
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
                        // 只设置圆心，完全不改变地图视角
                        ACTION_SET_CENTER -> setCenter(fix, moveCamera = false)
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
                // 只设置圆心，完全不改变地图视角
                ACTION_SET_CENTER -> setCenter(known, moveCamera = false)
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

    // ------------------------------------------------------------------ 已保存区域

    private fun setupZoneList() {
        zoneAdapter = CircleZoneAdapter(
            onItemClick = { zone -> focusZone(zone) },
            onItemDelete = { zone -> deleteZone(zone) }
        )
        binding.rvZones.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvZones.adapter = zoneAdapter
        refreshZones()
    }

    private fun refreshZones() {
        zones.clear()
        zones.addAll(CircleStore.loadAll())
        zoneAdapter.submit(zones)
        renderSavedZones()

        val hasZones = zones.isNotEmpty()
        binding.rvZones.visibility = if (hasZones) View.VISIBLE else View.GONE
        binding.tvZonesEmpty.visibility = if (hasZones) View.GONE else View.VISIBLE
        binding.tvSavedTitle.text = getString(R.string.fmt_saved_count, zones.size)
        binding.btnClearAll.isEnabled = hasZones
    }

    /**
     * 把所有「已保存区域」常驻绘制到地图上：圆形 + 圆心点 + 名称标签。
     *
     * 关键点：不是只画"当前编辑"的那一个，而是全部同时显示，
     * 这样多个圆的重叠 / 相交关系才能一眼看出来。
     * 每个区域按列表位置取一个颜色，与列表卡片上的色点一致。
     */
    private fun renderSavedZones() {
        // 先移除上一次绘制的覆盖物
        zoneOverlays.values.forEach { list -> list.forEach { it.remove() } }
        zoneOverlays.clear()

        zones.forEachIndexed { index, zone ->
            val color = ZoneColors.colorAt(index)
            val center = LatLng(zone.latitude, zone.longitude)
            val meters = (zone.radiusKm * 1000.0).toInt().coerceAtLeast(1)

            val overlays = mutableListOf<Overlay>()

            overlays.add(
                baiduMap.addOverlay(
                    CircleOptions()
                        .center(center)
                        .radius(meters)
                        .fillColor(ZoneColors.fill(color))
                        .stroke(Stroke(SAVED_STROKE_WIDTH_PX, color))
                )
            )
            overlays.add(
                baiduMap.addOverlay(
                    MarkerOptions()
                        .position(center)
                        .icon(buildDotIcon(color))
                        .anchor(0.5f, 0.5f)
                )
            )
            overlays.add(
                baiduMap.addOverlay(
                    MarkerOptions()
                        .position(center)
                        .icon(buildLabelIcon(zone.name, color))
                        .anchor(0.5f, 1.0f)
                )
            )

            zoneOverlays[zone.id] = overlays
        }
    }

    /** 已保存区域的圆心点：外白圈 + 内彩色实心圆 */
    private fun buildDotIcon(color: Int): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val size = (13 * density).toInt().coerceAtLeast(16)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.density = resources.displayMetrics.densityDpi

        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(r, r, r, paint)

        paint.color = color
        canvas.drawCircle(r, r, r * 0.70f, paint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    /** 已保存区域的名称标签：圆角底色 + 白字，锚点在底部中点，显示在圆心上方 */
    private fun buildLabelIcon(text: String, color: Int): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val padH = 6f * density
        val padV = 3f * density

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.textSize = 11f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE

        val fm = textPaint.fontMetrics
        val textW = textPaint.measureText(text)
        val textH = fm.descent - fm.ascent

        val w = (textW + padH * 2f).toInt().coerceAtLeast(1)
        val h = (textH + padV * 2f).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.density = resources.displayMetrics.densityDpi

        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = color
        val corner = 4f * density
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, bg)

        canvas.drawText(text, padH, padV - fm.ascent, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun saveCurrentZone() {
        val center = centerLatLng
        if (center == null) {
            toast(getString(R.string.toast_need_center))
            return
        }

        val now = System.currentTimeMillis()
        val name = getString(R.string.default_zone_name, zones.size + 1)
        val zone = CircleZone(
            id = now,
            name = name,
            latitude = center.latitude,
            longitude = center.longitude,
            radiusKm = radiusKm,
            createdAt = now
        )
        CircleStore.add(zone)
        refreshZones()
        // 草稿圆已经变成「已保存区域」并常驻显示，清掉草稿避免地图上出现两个重合的圆
        clearCircle()
        toast(getString(R.string.toast_saved, name))
    }

    private fun deleteZone(zone: CircleZone) {
        CircleStore.remove(zone.id)
        refreshZones()
        toast(getString(R.string.toast_deleted, zone.name))
    }

    private fun focusZone(zone: CircleZone) {
        // 已保存区域本来就常驻显示在地图上，这里只移动视角 + 把参数回填到输入框，
        // 不再额外创建草稿圆，避免和已保存的那个重合。
        radiusKm = zone.radiusKm
        binding.etRadius.setText(GeoUtils.formatKm(zone.radiusKm))
        baiduMap.animateMapStatus(
            MapStatusUpdateFactory.newLatLngZoom(
                LatLng(zone.latitude, zone.longitude),
                zoomForRadius(zone.radiusKm)
            )
        )
        toast(getString(R.string.toast_zone_loaded, zone.name))
    }

    private fun clearAllZones() {
        if (zones.isEmpty()) {
            toast(getString(R.string.toast_nothing_to_clear))
            return
        }
        val count = zones.size
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_clear_all_title)
            .setMessage(getString(R.string.dialog_clear_all_message, count))
            .setPositiveButton(R.string.dialog_clear_confirm) { _, _ ->
                CircleStore.clear()
                refreshZones()
                toast(getString(R.string.toast_cleared_all, count))
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ 按钮

    private fun setupButtons() {
        binding.btnLocate.setOnClickListener {
            ensureLocationThen(ACTION_MOVE_CAMERA)
        }

        binding.btnUseLocationAsCenter.setOnClickListener {
            ensureLocationThen(ACTION_SET_CENTER)
        }

        binding.btnSave.setOnClickListener {
            saveCurrentZone()
        }

        binding.btnClear.setOnClickListener {
            clearCircle()
        }

        binding.btnClearAll.setOnClickListener {
            clearAllZones()
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

        /** 已保存区域圆形的描边宽度 */
        private const val SAVED_STROKE_WIDTH_PX = 3
    }
}
