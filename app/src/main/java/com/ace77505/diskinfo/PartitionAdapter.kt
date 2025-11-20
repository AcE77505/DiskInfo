package com.ace77505.diskinfo

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ace77505.diskinfo.data.PartitionInfo
import com.ace77505.diskinfo.data.PartitionType
import java.util.Locale

class PartitionAdapter(
    private var partitions: List<PartitionInfo>) : RecyclerView.Adapter<PartitionAdapter.PartitionViewHolder>() {

    // 预加载的颜色资源
    private var loopPartitionColor: Int = 0
    private var superPartitionColor: Int = 0
    private var defaultPartitionColor: Int = 0

    // 颜色初始化标志
    private var colorsInitialized: Boolean = false

    // 字体大小配置
    var displaySizeScale: Float = 1.0f
    var partitionNameSize: Float = 1.0f
    var usageSize: Float = 1.0f
    var otherTextSize: Float = 1.0f
    var hideLoopDevices: Boolean = false

    // 点击监听器接口
    interface OnItemClickListener {
        fun onItemClick(partition: PartitionInfo)
    }

    private var onItemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.onItemClickListener = listener
    }

    // 初始化颜色资源
    fun initializeColors(context: Context) {
        loopPartitionColor = ContextCompat.getColor(context, R.color.loop_partition_color)
        superPartitionColor = ContextCompat.getColor(context, R.color.super_partition_color)
        defaultPartitionColor = ContextCompat.getColor(context, R.color.default_partition_color)
        colorsInitialized = true
    }

    class PartitionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val partitionName: TextView = itemView.findViewById(R.id.partitionName)
        val devicePath: TextView = itemView.findViewById(R.id.devicePath)
        val mountStatusText: TextView = itemView.findViewById(R.id.mountStatusText)
        val progressBackground: View = itemView.findViewById(R.id.progressBackground)
        val progressFill: View = itemView.findViewById(R.id.progressFill)
        val spaceInfo: TextView = itemView.findViewById(R.id.spaceInfo)
        val usagePercentage: TextView = itemView.findViewById(R.id.usagePercentage)
        val filesystemType: TextView = itemView.findViewById(R.id.filesystemType)
        val cardView: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.card_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartitionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partition, parent, false)
        return PartitionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartitionViewHolder, position: Int) {
        val partition = partitions[position]

        holder.partitionName.text = partition.name
        holder.devicePath.text = partition.devicePath
        holder.filesystemType.text = partition.fileSystemType

        // 优化后的卡片背景颜色设置
        setCardBackgroundColor(holder, partition)

        // 应用字体大小设置
        applyTextSizeSettings(holder)

        // 设置挂载状态
        setupMountStatus(holder, partition)

        // 设置空间使用信息
        setupSpaceInfo(holder, partition)

        // 设置点击监听器
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onItemClickListener?.onItemClick(partitions[adapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = partitions.size

    fun updateData(newPartitions: List<PartitionInfo>) {
        // === 添加性能日志开始 ===
        val startTime = System.currentTimeMillis()
        Log.d("Performance", "Adapter.updateData 开始，新分区数量: ${newPartitions.size}")
        // === 添加性能日志结束 ===

        val filteredPartitions = if (hideLoopDevices) {
            newPartitions.filter { it.partitionType != PartitionType.LOOP }
        } else {
            newPartitions
        }

        // === 添加 DiffUtil 计算性能日志 ===
        val diffStartTime = System.currentTimeMillis()
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            PartitionDiffCallback(partitions, filteredPartitions)
        )
        val diffDuration = System.currentTimeMillis() - diffStartTime
        Log.d("Performance", "DiffUtil 计算耗时: ${diffDuration}ms")
        // === 性能日志结束 ===

        partitions = filteredPartitions

        // === 添加更新通知性能日志 ===
        val notifyStartTime = System.currentTimeMillis()
        diffResult.dispatchUpdatesTo(this)
        val notifyDuration = System.currentTimeMillis() - notifyStartTime
        Log.d("Performance", "dispatchUpdatesTo 耗时: ${notifyDuration}ms")
        // === 性能日志结束 ===

        // === 添加总耗时日志 ===
        val totalDuration = System.currentTimeMillis() - startTime
        Log.d("Performance", "Adapter.updateData 总耗时: ${totalDuration}ms")
        // === 性能日志结束 ===
    }

    fun updateHideLoopDevicesSetting(shouldHide: Boolean) {
        // 无论设置是否改变，都要更新变量
        hideLoopDevices = shouldHide

        // 只有在数据已经存在的情况下才立即过滤，否则等待数据加载
        if (partitions.isNotEmpty()) {
            val filteredPartitions = if (hideLoopDevices) {
                partitions.filter { it.partitionType != PartitionType.LOOP }
            } else {
                partitions
            }

            // 使用 DiffUtil 更新数据
            val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(
                PartitionDiffCallback(partitions, filteredPartitions)
            )
            partitions = filteredPartitions
            diffResult.dispatchUpdatesTo(this)
        }
    }

    // 优化后的方法：设置卡片背景颜色 - 简化版本
    private fun setCardBackgroundColor(holder: PartitionViewHolder, partition: PartitionInfo) {
        if (!colorsInitialized) {
            // 如果颜色未初始化，使用默认颜色
            holder.cardView.setCardBackgroundColor(defaultPartitionColor)
            return
        }

        val color = when (partition.partitionType) {
            PartitionType.LOOP -> loopPartitionColor
            PartitionType.SUPER -> superPartitionColor
            else -> defaultPartitionColor  // 所有其他分区使用默认颜色
        }
        holder.cardView.setCardBackgroundColor(color)
    }

    // 应用字体大小设置
    private fun applyTextSizeSettings(holder: PartitionViewHolder) {
        // 基础字体大小
        val basePartitionNameSize = 18f
        val baseUsageSize = 16f
        val baseOtherTextSize = 14f

        // 应用缩放
        holder.partitionName.textSize = basePartitionNameSize * partitionNameSize * displaySizeScale
        holder.spaceInfo.textSize = baseUsageSize * usageSize * displaySizeScale
        holder.usagePercentage.textSize = baseUsageSize * usageSize * displaySizeScale
        holder.devicePath.textSize = baseOtherTextSize * otherTextSize * displaySizeScale
        holder.filesystemType.textSize = baseOtherTextSize * otherTextSize * displaySizeScale
        holder.mountStatusText.textSize = baseOtherTextSize * otherTextSize * displaySizeScale
    }

    // 更新字体大小设置
    @SuppressLint("NotifyDataSetChanged")
    fun updateTextSizeSettings(
        displayScale: Float,
        partitionNameScale: Float,
        usageScale: Float,
        otherTextScale: Float
    ) {
        this.displaySizeScale = displayScale
        this.partitionNameSize = partitionNameScale
        this.usageSize = usageScale
        this.otherTextSize = otherTextScale
        notifyDataSetChanged()
    }

    private fun setupMountStatus(holder: PartitionViewHolder, partition: PartitionInfo) {
        when {
            !partition.isMounted -> {
                holder.mountStatusText.visibility = View.GONE
                holder.filesystemType.visibility = View.GONE
            }
            partition.isReadOnly -> {
                holder.mountStatusText.text = holder.itemView.context.getString(R.string.read_only)
                holder.mountStatusText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
                holder.mountStatusText.visibility = View.VISIBLE
                holder.filesystemType.visibility = View.VISIBLE
            }
            else -> {
                holder.mountStatusText.text = holder.itemView.context.getString(R.string.read_write)
                holder.mountStatusText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
                holder.mountStatusText.visibility = View.VISIBLE
                holder.filesystemType.visibility = View.VISIBLE
            }
        }
    }

    private fun setupSpaceInfo(holder: PartitionViewHolder, partition: PartitionInfo) {
        if (partition.size > 0) {
            val formattedSize = com.ace77505.diskinfo.data.PartitionDataManager.formatBytes(partition.size)

            if (partition.isMounted && partition.totalSpace > 0) {
                val usedSpace = partition.usedSpace
                val totalSpace = partition.totalSpace
                val usagePercent = if (totalSpace > 0) {
                    (usedSpace.toDouble() / totalSpace.toDouble() * 100).toInt()
                } else {
                    0
                }

                holder.progressBackground.post {
                    val totalWidth = holder.progressBackground.width
                    val progressWidth = (totalWidth * usagePercent / 100)

                    val layoutParams = holder.progressFill.layoutParams
                    layoutParams.width = progressWidth
                    holder.progressFill.layoutParams = layoutParams
                }

                holder.progressBackground.visibility = View.VISIBLE
                holder.progressFill.visibility = View.VISIBLE

                holder.spaceInfo.text = String.format(
                    Locale.getDefault(),
                    "%s / %s",
                    com.ace77505.diskinfo.data.PartitionDataManager.formatBytes(usedSpace),
                    com.ace77505.diskinfo.data.PartitionDataManager.formatBytes(totalSpace)
                )
                holder.usagePercentage.text = String.format(Locale.getDefault(), "%d%%", usagePercent)
            } else {
                holder.progressBackground.visibility = View.GONE
                holder.progressFill.visibility = View.GONE
                holder.spaceInfo.text = formattedSize
                holder.usagePercentage.text = ""
            }
        } else {
            holder.progressBackground.visibility = View.GONE
            holder.progressFill.visibility = View.GONE
            holder.spaceInfo.text = ""
            holder.usagePercentage.text = ""
        }
    }

    private class PartitionDiffCallback(
        private val oldList: List<PartitionInfo>,
        private val newList: List<PartitionInfo>
    ) : androidx.recyclerview.widget.DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].devicePath == newList[newItemPosition].devicePath
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}