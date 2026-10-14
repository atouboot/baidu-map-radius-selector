package com.atouboot.radiusmap.ui

import android.graphics.Color

/**
 * 已保存区域的配色。
 *
 * 每个区域按其在列表中的位置取一个颜色，地图上的圆形、圆心点、名称标签，
 * 以及列表卡片上的色点，都用同一个颜色，方便对应。
 */
object ZoneColors {

    private val PALETTE = intArrayOf(
        Color.parseColor("#E53935"), // 红
        Color.parseColor("#1E88E5"), // 蓝
        Color.parseColor("#43A047"), // 绿
        Color.parseColor("#FB8C00"), // 橙
        Color.parseColor("#8E24AA"), // 紫
        Color.parseColor("#00ACC1"), // 青
        Color.parseColor("#C0CA33"), // 黄绿
        Color.parseColor("#6D4C41")  // 棕
    )

    val size: Int get() = PALETTE.size

    /** 取第 index 个颜色（自动取模，支持无限多个区域） */
    fun colorAt(index: Int): Int {
        val i = ((index % PALETTE.size) + PALETTE.size) % PALETTE.size
        return PALETTE[i]
    }

    /** 圆形填充色：同一色相、约 22% 不透明度，保证多个圆重叠时仍能看清 */
    fun fill(color: Int): Int =
        Color.argb(56, Color.red(color), Color.green(color), Color.blue(color))
}
