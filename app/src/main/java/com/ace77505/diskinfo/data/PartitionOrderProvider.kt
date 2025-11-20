package com.ace77505.diskinfo.data

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern

/**
 * 分区顺序提供器
 */
object PartitionOrderProvider {

    private const val PROC_PARTITIONS = "/proc/partitions"
    private const val SYS_BLOCK = "/sys/block"

    /**
     * 获取按 /proc/partitions 顺序排列的设备列表
     */
    fun getOrderedBlockDevices(partitionNames: Map<String, String>, debugInfo: StringBuilder): List<String> {
        val devices = mutableListOf<String>()

        try {
            // 从 /proc/partitions 读取分区信息，保持文件中的顺序
            val partitionsFile = File(PROC_PARTITIONS)
            if (partitionsFile.exists()) {
                BufferedReader(FileReader(PROC_PARTITIONS)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            if (it.trim().isNotEmpty() && !it.startsWith("major")) {
                                val parts = it.trim().split(Pattern.compile("\\s+"))
                                if (parts.size >= 4) {
                                    val deviceName = parts[3]
                                    // 过滤掉非块设备和 ram 设备
                                    if (isValidBlockDevice(deviceName) && !deviceName.startsWith("ram")) {
                                        devices.add(deviceName)
                                        debugInfo.append("Added device from partitions: $deviceName\n")
                                    } else {
                                        debugInfo.append("Skipped invalid or ram device: $deviceName\n")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 如果 /proc/partitions 为空，尝试从 /sys/block 读取
            if (devices.isEmpty()) {
                addDevicesFromSysBlock(devices, debugInfo)
            }

            // 重要修复：只添加实际存在的设备，不添加虚假设备
            val missingDevices = partitionNames.keys - devices.toSet()

            if (missingDevices.isNotEmpty()) {
                debugInfo.append("=== 发现缺失的设备 ===\n")
                missingDevices.forEach { device ->
                    // 只添加确实存在的块设备，排除 ram 设备
                    if (isValidBlockDevice(device) && !device.startsWith("ram") &&
                        PartitionDeviceValidator.isPhysicalDeviceExists(device, debugInfo)) {
                        debugInfo.append("添加确实存在的设备: $device\n")
                        devices.add(device)
                    } else {
                        debugInfo.append("跳过无效或 ram 设备: $device\n")
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        debugInfo.append("最终设备列表: ${devices.sorted()}\n")
        debugInfo.append("最终设备列表大小: ${devices.size}\n")

        return devices.distinct()
    }

    /**
     * 判断是否为有效的块设备（不是目录或符号链接）
     */
    private fun isValidBlockDevice(deviceName: String): Boolean {
        // 排除目录和符号链接
        if (deviceName == "vold" ||
            deviceName == "bootdevice" ||
            deviceName == "by-name" ||
            deviceName == "platform" ||
            deviceName == "mapper") {
            return false
        }

        // 排除 ram 设备
        if (deviceName.startsWith("ram")) {
            return false
        }

        // 排除明显不是块设备的名称
        if (deviceName.contains("/") || deviceName.contains(".")) {
            return false
        }

        // 有效的块设备模式
        return deviceName.startsWith("mmcblk") ||
                deviceName.startsWith("sd") ||
                deviceName.startsWith("dm-") ||
                deviceName.startsWith("loop") ||
                deviceName.matches(Regex("^[a-z]+\\d*$"))
    }

    /**
     * 从 /sys/block 添加设备
     */
    private fun addDevicesFromSysBlock(devices: MutableList<String>, debugInfo: StringBuilder) {
        val sysBlockDir = File(SYS_BLOCK)
        if (sysBlockDir.exists() && sysBlockDir.isDirectory) {
            sysBlockDir.listFiles()?.forEach { file ->
                // 只添加块设备，排除目录和 ram 设备
                if (file.isDirectory && isValidBlockDevice(file.name) && !file.name.startsWith("ram")) {
                    debugInfo.append("Adding block device from sys/block: ${file.name}\n")
                    // 获取该块设备下的分区，按名称排序
                    file.listFiles()?.sortedBy { it.name }?.forEach { partitionFile ->
                        val partitionName = partitionFile.name
                        if (isValidBlockDevice(partitionName) && !partitionName.startsWith("ram")) {
                            devices.add(partitionName)
                            debugInfo.append("  Added partition: $partitionName\n")
                        }
                    }
                }
            }
        }
    }
}