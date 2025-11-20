package com.ace77505.diskinfo.data

import java.io.File

/**
 * 分区设备验证器
 */
object PartitionDeviceValidator {

    /**
     * 检查是否为有效的分区名称
     */
    fun isValidPartition(deviceName: String): Boolean {
        // 排除 ram 设备
        return when {
            deviceName.startsWith("ram") -> false // 过滤掉所有 ram 设备
            deviceName.startsWith("loop") -> true // 包含 loop 设备
            deviceName.startsWith("dm-") -> true // 包含 device mapper 设备
            deviceName.startsWith("zram") -> true // 包含 zram 设备
            else -> true // 包含所有其他设备
        }
    }

    /**
     * 检查物理设备是否存在
     */
    fun isPhysicalDeviceExists(deviceName: String, debugInfo: StringBuilder? = null): Boolean {
        // 首先检查是否为 ram 设备
        if (deviceName.startsWith("ram")) {
            debugInfo?.append("设备 $deviceName 是 ram 设备，跳过\n")
            return false
        }

        return try {
            // 方法1：检查 /dev/block/ 下的设备文件
            val deviceFile = File("/dev/block/$deviceName")
            if (deviceFile.exists()) {
                // 进一步检查是否是块设备（不是目录或普通文件）
                if (deviceFile.isFile) {
                    debugInfo?.append("设备 $deviceName 在 /dev/block/ 中存在且是文件\n")
                    return true
                }
            }

            // 方法2：检查 /sys/block/ 下的设备信息
            val blockDevice = getBlockDeviceName(deviceName)
            val sysDevicePath = "/sys/block/$blockDevice/$deviceName"
            val sysDeviceFile = File(sysDevicePath)
            if (sysDeviceFile.exists()) {
                debugInfo?.append("设备 $deviceName 在 $sysDevicePath 中存在\n")
                return true
            }

            // 方法3：检查设备大小（如果大小为0，可能是无效设备）
            val size = PartitionSizeCalculator.getPartitionSize(deviceName)
            if (size > 0) {
                debugInfo?.append("设备 $deviceName 有有效大小: $size\n")
                return true
            }

            debugInfo?.append("设备 $deviceName 不存在或无效\n")
            false
        } catch (e: Exception) {
            debugInfo?.append("检查设备 $deviceName 存在性时出错: ${e.message}\n")
            false
        }
    }

    /**
     * 获取块设备名称（去掉分区号）
     */
    private fun getBlockDeviceName(deviceName: String): String {
        return when {
            deviceName.startsWith("mmcblk") -> {
                deviceName.replace(Regex("p\\d+$"), "")
            }
            deviceName.matches(Regex("^[a-z]+\\d+$")) -> {
                deviceName.replace(Regex("\\d+$"), "")
            }
            else -> deviceName
        }
    }

    /**
     * 检查文件是否是块设备的符号链接
     */
    fun isBlockDeviceSymlink(file: File): Boolean {
        return try {
            if (!file.exists()) return false

            // 检查是否是符号链接
            val isSymlink = File(file.parent ?: "", file.name).let {
                it.canonicalPath != it.absolutePath
            }

            if (!isSymlink) return false

            // 检查目标是否是块设备（以 sdX 或 mmcblkX 开头），排除 ram 设备
            val targetName = File(file.canonicalPath).name
            (targetName.startsWith("sd") || targetName.startsWith("mmcblk")) && !targetName.startsWith("ram")
        } catch (_: Exception) {
            false
        }
    }
}