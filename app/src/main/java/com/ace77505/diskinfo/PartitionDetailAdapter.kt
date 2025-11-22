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

    // 定义卡片类型
    companion object {
        private const val TYPE_DEVICE_NAME = 0
        private const val TYPE_STORAGE_INFO = 1
        private const val TYPE_MOUNT_POINTS = 2
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 设备名卡片视图
        val deviceNameValue: com.google.android.material.textview.MaterialTextView? = itemView.findViewById(R.id.device_name_value)

        // 存储信息卡片视图
        val filesystemValue: com.google.android.material.textview.MaterialTextView? = itemView.findViewById(R.id.filesystem_value)
        val partitionSizeValue: com.google.android.material.textview.MaterialTextView? = itemView.findViewById(R.id.partition_size_value)
        val usedSpaceValue: com.google.android.material.textview.MaterialTextView? = itemView.findViewById(R.id.used_space_value)

        // 挂载点卡片视图
        val mountPointsValue: com.google.android.material.textview.MaterialTextView? = itemView.findViewById(R.id.mount_points_value)
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_DEVICE_NAME
            1 -> TYPE_STORAGE_INFO
            2 -> TYPE_MOUNT_POINTS
            else -> TYPE_DEVICE_NAME
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = when (viewType) {
            TYPE_DEVICE_NAME -> R.layout.item_device_name_card
            TYPE_STORAGE_INFO -> R.layout.item_storage_info_card
            TYPE_MOUNT_POINTS -> R.layout.item_mount_points_card
            else -> R.layout.item_device_name_card
        }

        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val partition = partition ?: return

        when (position) {
            TYPE_DEVICE_NAME -> {
                // 设备名卡片
                val deviceName = partition.devicePath.substringAfterLast('/')
                holder.deviceNameValue?.text = deviceName
            }
            TYPE_STORAGE_INFO -> {
                // 存储信息卡片（文件系统、分区大小、已用空间）
                holder.filesystemValue?.text = partition.fileSystemType
                holder.partitionSizeValue?.text = PartitionDataManager.formatBytes(partition.size)
                holder.usedSpaceValue?.text = PartitionDataManager.formatBytes(partition.usedSpace)
            }
            TYPE_MOUNT_POINTS -> {
                // 挂载点卡片
                val mountPoints = getAllMountPointsFromSystem(partition)
                if (mountPoints.isNotEmpty()) {
                    val sortedMountPoints = sortMountPointsIfNeeded(partition, mountPoints)
                    holder.mountPointsValue?.text = sortedMountPoints.joinToString("\n")
                } else {
                    holder.mountPointsValue?.text = "未挂载"
                }
            }
        }
    }

    override fun getItemCount(): Int = 3 // 3个卡片：设备名、存储信息、挂载点

    fun updateData(newPartition: PartitionInfo) {
        partition = newPartition
        notifyDataSetChanged()
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