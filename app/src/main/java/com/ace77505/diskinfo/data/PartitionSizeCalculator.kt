package com.ace77505.diskinfo.data

import java.io.File

/**
 * 分区大小计算器
 */
object PartitionSizeCalculator {

    private const val SYS_BLOCK = "/sys/block"

    /**
     * 获取分区大小
     */
    fun getPartitionSize(deviceName: String, addDebugInfo: ((String, String) -> Unit)? = null): Long {
        return try {
            // 方法1: 从 /sys/block 读取
            val sizeFromSys = getSizeFromSysBlock(deviceName, addDebugInfo)
            if (sizeFromSys > 0) {
                return sizeFromSys
            }

            // 方法2: 从 /proc/partitions 读取
            val sizeFromProc = getSizeFromProcPartitions(deviceName, addDebugInfo)
            if (sizeFromProc > 0) {
                return sizeFromProc
            }

            // 方法3: 尝试直接读取设备文件信息
            getSizeFromDeviceFile(deviceName, addDebugInfo)
        } catch (e: Exception) {
            addDebugInfo?.invoke(deviceName, "ERROR getting partition size: ${e.message}")
            0L
        }
    }

    private fun getSizeFromSysBlock(deviceName: String, addDebugInfo: ((String, String) -> Unit)?): Long {
        return try {
            val blockDevice = getBlockDeviceName(deviceName)
            val sizeFile = File("$SYS_BLOCK/$blockDevice/$deviceName/size")

            if (sizeFile.exists()) {
                val sizeText = sizeFile.readText().trim()
                val blocks = sizeText.toLongOrNull() ?: 0L
                val size = blocks * 512 // 块大小通常是512字节

                addDebugInfo?.invoke(deviceName, "Size from sys/block: $blocks blocks = $size bytes")
                size
            } else {
                0L
            }
        } catch (e: Exception) {
            addDebugInfo?.invoke(deviceName, "ERROR reading size from sys/block: ${e.message}")
            0L
        }
    }

    private fun getSizeFromProcPartitions(deviceName: String, addDebugInfo: ((String, String) -> Unit)?): Long {
        return try {
            val partitionsFile = File("/proc/partitions")
            if (partitionsFile.exists()) {
                partitionsFile.readLines().forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 4 && parts[3] == deviceName) {
                        val blocks = parts[2].toLongOrNull() ?: 0L
                        val size = blocks * 1024 // /proc/partitions 使用1KB块

                        addDebugInfo?.invoke(deviceName, "Size from proc/partitions: $blocks blocks = $size bytes")
                        return size
                    }
                }
            }
            0L
        } catch (e: Exception) {
            addDebugInfo?.invoke(deviceName, "ERROR reading size from proc/partitions: ${e.message}")
            0L
        }
    }

    private fun getSizeFromDeviceFile(deviceName: String, addDebugInfo: ((String, String) -> Unit)?): Long {
        return try {
            val deviceFile = File("/dev/block/$deviceName")
            if (deviceFile.exists()) {
                // 使用 File.length() 获取大小（可能不准确）
                val size = deviceFile.length()
                addDebugInfo?.invoke(deviceName, "Size from device file: $size bytes")
                size
            } else {
                0L
            }
        } catch (e: Exception) {
            addDebugInfo?.invoke(deviceName, "ERROR reading size from device file: ${e.message}")
            0L
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
}