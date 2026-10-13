package com.atouboot.radiusmap

import android.app.Application
import com.atouboot.radiusmap.data.AppPrefs

/**
 * 应用入口：只负责初始化本地配置存储。
 *
 * 注意：百度 SDK 的初始化放在 MainActivity，因为需要先确认 AK 是否可用。
 */
class RadiusMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(this)
    }
}
