package com.atouboot.radiusmap.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地配置存储（SharedPreferences）。
 *
 * 保存两类数据：
 *  - 百度地图 AK：用户私有凭证，只在设备本地保存，不参与版本控制、不上传服务器
 *  - 已保存的圆形区域：一段 JSON
 */
object AppPrefs {

    private const val FILE_NAME = "radius_map_prefs"
    private const val KEY_BAIDU_AK = "baidu_map_ak"
    private const val KEY_ZONES = "circle_zones"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext
                        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun require(): SharedPreferences =
        prefs ?: throw IllegalStateException("AppPrefs 未初始化，请先在 Application 中调用 init()")

    // ---------------------------------------------------------------- AK

    /** 用户在 App 内填写的 AK（可能为空） */
    fun getBaiduAk(): String = require().getString(KEY_BAIDU_AK, "").orEmpty()

    fun setBaiduAk(ak: String) {
        require().edit().putString(KEY_BAIDU_AK, ak.trim()).apply()
    }

    // ---------------------------------------------------------------- 圆形区域

    fun getZonesJson(): String = require().getString(KEY_ZONES, "").orEmpty()

    fun setZonesJson(json: String) {
        require().edit().putString(KEY_ZONES, json).apply()
    }
}
