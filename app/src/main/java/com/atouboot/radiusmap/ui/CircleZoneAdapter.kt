package com.atouboot.radiusmap.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.atouboot.radiusmap.R
import com.atouboot.radiusmap.data.CircleZone
import com.atouboot.radiusmap.databinding.ItemCircleZoneBinding
import com.atouboot.radiusmap.util.GeoUtils

/**
 * 「已保存区域」横向列表适配器。
 *
 * 交互：
 *  - 点击卡片：地图定位到该区域并显示其圆形
 *  - 点击右上角 ✕：删除该区域
 */
class CircleZoneAdapter(
    private val onItemClick: (CircleZone) -> Unit,
    private val onItemDelete: (CircleZone) -> Unit
) : RecyclerView.Adapter<CircleZoneAdapter.ZoneViewHolder>() {

    private val items = mutableListOf<CircleZone>()

    fun submit(list: List<CircleZone>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZoneViewHolder {
        val binding = ItemCircleZoneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ZoneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ZoneViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ZoneViewHolder(
        private val binding: ItemCircleZoneBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(zone: CircleZone) {
            binding.tvName.text = zone.name
            binding.tvMeta.text = binding.root.context.getString(
                R.string.fmt_zone_meta,
                GeoUtils.formatKm(zone.radiusKm),
                GeoUtils.formatLatLng(zone.latitude, zone.longitude)
            )

            binding.btnDelete.visibility = View.VISIBLE
            binding.btnDelete.setOnClickListener { onItemDelete(zone) }
            binding.root.setOnClickListener { onItemClick(zone) }
        }
    }
}
