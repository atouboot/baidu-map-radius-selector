package com.atouboot.radiusmap.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个已保存的圆形区域。
 *
 * 使用 org.json（Android 内置）做序列化，避免引入额外依赖。
 */
data class CircleZone(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("lat", latitude)
        put("lng", longitude)
        put("radiusKm", radiusKm)
        put("createdAt", createdAt)
    }

    companion object {

        fun fromJson(obj: JSONObject): CircleZone = CircleZone(
            id = obj.optLong("id", 0L),
            name = obj.optString("name", ""),
            latitude = obj.optDouble("lat", 0.0),
            longitude = obj.optDouble("lng", 0.0),
            radiusKm = obj.optDouble("radiusKm", 1.0),
            createdAt = obj.optLong("createdAt", 0L)
        )

        fun toJsonArray(list: List<CircleZone>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        /**
         * 解析 JSON；数据损坏时返回空列表而不是抛异常，避免 App 启动崩溃。
         */
        fun fromJsonArray(raw: String?): MutableList<CircleZone> {
            val result = mutableListOf<CircleZone>()
            if (raw.isNullOrBlank()) return result
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    result.add(fromJson(obj))
                }
            } catch (t: Throwable) {
                // 忽略损坏数据
            }
            return result
        }
    }
}
