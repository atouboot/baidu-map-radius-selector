package com.atouboot.radiusmap.util

import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理计算工具。
 *
 * 说明：百度地图 SDK 的 DistanceUtil 在部分版本中不在 Map 包内，
 * 为避免依赖不确定性，这里自行实现 Haversine 公式。
 */
object GeoUtils {

    /** 地球平均半径（米），IUGG 推荐值 */
    private const val EARTH_RADIUS_M = 6371008.8

    /** 两点间距离，单位：米 */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lng2 - lng1)

        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /** 圆形覆盖面积，单位：km² */
    fun areaKm2(radiusKm: Double): Double = PI * radiusKm * radiusKm

    /** 经纬度格式化：保留 6 位小数（约 0.1 m 精度） */
    fun formatLatLng(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.6f, %.6f", lat, lng)

    /** 半径格式化：保留 2 位小数 */
    fun formatKm(radiusKm: Double): String =
        String.format(Locale.US, "%.2f", radiusKm)
}
