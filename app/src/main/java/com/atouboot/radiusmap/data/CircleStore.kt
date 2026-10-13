package com.atouboot.radiusmap.data

/**
 * 已保存圆形区域的仓库：负责读取 / 写入 / 增删。
 *
 * 底层存储为 SharedPreferences 中的一段 JSON。
 */
object CircleStore {

    /** 按创建时间倒序读取全部区域（最新的在最前） */
    fun loadAll(): MutableList<CircleZone> {
        val list = CircleZone.fromJsonArray(AppPrefs.getZonesJson())
        list.sortByDescending { it.createdAt }
        return list
    }

    fun saveAll(list: List<CircleZone>) {
        AppPrefs.setZonesJson(CircleZone.toJsonArray(list))
    }

    /** 新增一条，返回新列表 */
    fun add(zone: CircleZone): MutableList<CircleZone> {
        val list = loadAll()
        list.add(0, zone)
        saveAll(list)
        return list
    }

    /** 按 id 删除，返回新列表 */
    fun remove(id: Long): MutableList<CircleZone> {
        val list = loadAll()
        list.removeAll { it.id == id }
        saveAll(list)
        return list
    }

    /** 清空全部 */
    fun clear(): MutableList<CircleZone> {
        saveAll(emptyList())
        return mutableListOf()
    }
}
