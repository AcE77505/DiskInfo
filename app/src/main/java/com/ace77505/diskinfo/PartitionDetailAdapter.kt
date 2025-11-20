package com.ace77505.diskinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ace77505.diskinfo.data.PartitionDataManager
import com.ace77505.diskinfo.data.PartitionInfo
import java.io.BufferedReader
import java.io.FileReader

class PartitionDetailAdapter : RecyclerView.Adapter<PartitionDetailAdapter.ViewHolder>() {

    private var partition: PartitionInfo? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: com.google.android.material.textview.MaterialTextView = itemView.findViewById(R.id.detail_title)
        private val valueTextView: com.google.android.material.textview.MaterialTextView = itemView.findViewById(R.id.detail_value)

        fun bind(title: String, value: String) {
            titleTextView.text = title
            valueTextView.text = value
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partition_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val partition = partition ?: return

        when (position) {
            0 -> {
                // 设备名
                val deviceName = partition.devicePath.substringAfterLast('/')
                holder.bind("设备名", deviceName)
            }
            1 -> {
                // 文件系统
                holder.bind("文件系统", partition.fileSystemType)
            }
            2 -> {
                // 分区大小
                holder.bind("分区大小", PartitionDataManager.formatBytes(partition.size))
            }
            3 -> {
                // 已用空间
                holder.bind("已用空间", PartitionDataManager.formatBytes(partition.usedSpace))
            }
            4 -> {
                // 挂载点 - 从系统获取所有相关挂载点
                val mountPoints = getAllMountPointsFromSystem(partition)
                if (mountPoints.isNotEmpty()) {
                    val sortedMountPoints = sortMountPointsIfNeeded(partition, mountPoints)
                    holder.bind("挂载点", sortedMountPoints.joinToString("\n"))
                } else {
                    holder.bind("挂载点", "未挂载")
                }
            }
        }
    }

    override fun getItemCount(): Int = 5 // 设备名、文件系统、分区大小、已用空间、挂载点

    fun updateData(newPartition: PartitionInfo) {
        val oldPartition = partition
        partition = newPartition

        // 使用更高效的更新方式
        if (oldPartition == null) {
            // 第一次设置数据
            notifyItemRangeInserted(0, itemCount)
        } else {
            // 数据更新，检查哪些项目发生了变化
            for (i in 0 until itemCount) {
                if (hasItemChanged(oldPartition, newPartition, i)) {
                    notifyItemChanged(i)
                }
            }
        }
    }

    /**
     * 检查指定位置的项目是否发生变化
     */
    private fun hasItemChanged(oldPartition: PartitionInfo, newPartition: PartitionInfo, position: Int): Boolean {
        return when (position) {
            0 -> oldPartition.devicePath != newPartition.devicePath
            1 -> oldPartition.fileSystemType != newPartition.fileSystemType
            2 -> oldPartition.size != newPartition.size
            3 -> oldPartition.usedSpace != newPartition.usedSpace
            4 -> {
                val oldMountPoints = getAllMountPointsFromSystem(oldPartition)
                val newMountPoints = getAllMountPointsFromSystem(newPartition)
                oldMountPoints != newMountPoints
            }
            else -> false
        }
    }

    /**
     * 从 /proc/mounts 获取分区的所有挂载点
     */
    private fun getAllMountPointsFromSystem(partition: PartitionInfo): List<String> {
        val mountPoints = mutableListOf<String>()
        val deviceName = partition.devicePath.substringAfterLast('/')

        try {
            BufferedReader(FileReader("/proc/mounts")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { mountLine ->
                        val parts = mountLine.split(" ")
                        if (parts.size >= 4) {
                            val device = parts[0]
                            val mountPoint = parts[1]

                            // 检查设备是否匹配（包含 vold 设备匹配）
                            if (isDeviceMatch(device, deviceName)) {
                                mountPoints.add(mountPoint)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mountPoints
    }

    /**
     * 对特定分区的挂载点进行排序
     */
    private fun sortMountPointsIfNeeded(partition: PartitionInfo, mountPoints: List<String>): List<String> {
        // 检查是否需要排序的分区
        val shouldSort = isPartitionRequiresSorting(partition)

        return if (shouldSort && mountPoints.size > 1) {
            // 简单的字符串长度排序（升序：短的在前，长的在后）
            mountPoints.sortedBy { it.length }
        } else {
            // 保持原顺序
            mountPoints
        }
    }

    /**
     * 检查分区是否需要挂载点排序
     */
    private fun isPartitionRequiresSorting(partition: PartitionInfo): Boolean {
        val partitionName = partition.name.lowercase()

        // userdata 分区
        if (partitionName.contains("userdata")) {
            return true
        }

        // system 分区
        if (partitionName.contains("system")) {
            return true
        }

        // vendor 分区
        if (partitionName.contains("vendor")) {
            return true
        }

        // 使用您之前提供的 super/dm 设备判断逻辑
        if (partition.partitionType == com.ace77505.diskinfo.data.PartitionType.SUPER) {
            return true
        }

        // 检查是否为 dm 设备
        if (partition.devicePath.contains("dm-") || partition.name.startsWith("dm-")) {
            return true
        }

        return false
    }

    /**
     * 检查设备是否匹配
     */
    private fun isDeviceMatch(devicePath: String, deviceName: String): Boolean {
        // 1. 直接匹配设备名
        if (devicePath.endsWith("/$deviceName") || devicePath.endsWith(deviceName)) {
            return true
        }

        // 2. 匹配符号链接指向的设备
        if (devicePath.contains("/by-name/") || devicePath.contains("/mapper/") ||
            devicePath.contains("/platform/") || devicePath.contains("/bootdevice/")) {
            try {
                val canonicalPath = java.io.File(devicePath).canonicalPath
                val canonicalDeviceName = canonicalPath.substringAfterLast('/')
                if (canonicalDeviceName == deviceName) {
                    return true
                }
            } catch (_: Exception) {
                // 忽略错误
            }
        }

        // 3. 对于 vold 设备，检查对应的块设备
        if (devicePath.contains("/dev/block/vold/public:")) {
            return isVoldDeviceMatch(devicePath, deviceName)
        }

        // 4. 检查设备映射关系（如 dm-设备）
        if (devicePath.startsWith("/dev/block/dm-")) {
            return isDmDeviceMatch(devicePath, deviceName)
        }

        return false
    }

    /**
     * 检查 vold 设备是否匹配
     */
    private fun isVoldDeviceMatch(devicePath: String, deviceName: String): Boolean {
        try {
            // 从 vold 设备路径提取主次设备号
            val voldDeviceName = devicePath.substringAfterLast('/')
            if (voldDeviceName.startsWith("public:")) {
                val numbersPart = voldDeviceName.removePrefix("public:")
                val separator = if (numbersPart.contains(',')) ',' else ':'
                val numbers = numbersPart.split(separator)

                if (numbers.size >= 2) {
                    val major = numbers[0].toIntOrNull()
                    val minor = numbers[1].toIntOrNull()

                    if (major != null && minor != null) {
                        // 检查这个主次设备号是否对应目标设备
                        return isBlockDeviceMatch(major, minor, deviceName)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 通过主次设备号检查块设备是否匹配
     */
    private fun isBlockDeviceMatch(major: Int, minor: Int, targetDeviceName: String): Boolean {
        try {
            // 方法1: 从 /proc/partitions 查找
            BufferedReader(FileReader("/proc/partitions")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        val parts = it.trim().split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val partMajor = parts[0].toIntOrNull()
                            val partMinor = parts[1].toIntOrNull()
                            val deviceName = parts[3]

                            if (partMajor == major && partMinor == minor && deviceName == targetDeviceName) {
                                return true
                            }
                        }
                    }
                }
            }

            // 方法2: 从 /sys/dev/block 查找
            val devBlockDir = java.io.File("/sys/dev/block/$major:$minor")
            if (devBlockDir.exists()) {
                // 检查 uevent 文件
                val ueventFile = java.io.File(devBlockDir, "uevent")
                if (ueventFile.exists()) {
                    BufferedReader(FileReader(ueventFile)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line?.startsWith("DEVNAME=") == true) {
                                val devName = line.substringAfter("DEVNAME=")
                                if (devName == targetDeviceName) {
                                    return true
                                }
                            }
                        }
                    }
                }

                // 检查设备目录名
                if (devBlockDir.name == targetDeviceName) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 检查 device mapper 设备是否匹配
     */
    private fun isDmDeviceMatch(devicePath: String, deviceName: String): Boolean {
        try {
            // 检查 dm 设备指向的实际设备
            val dmName = devicePath.substringAfterLast('/')
            val dmDir = java.io.File("/sys/block/$dmName/slaves/")
            if (dmDir.exists() && dmDir.isDirectory) {
                dmDir.listFiles()?.forEach { slave ->
                    if (slave.name == deviceName) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {
            // 忽略错误
        }
        return false
    }
}