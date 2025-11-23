package com.ace77505.diskinfo.data

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * 挂载信息读取器
 */
object MountInfoReader {

    private const val PROC_MOUNTS = "/proc/mounts"

    /**
     * 获取挂载信息
     */
    fun getMountInfo(debugInfo: StringBuilder): Map<String, MountInfo> {
        val mountInfo = mutableMapOf<String, MountInfo>()

        try {
            BufferedReader(FileReader(PROC_MOUNTS)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { mountLine ->
                        val parts = mountLine.split(" ")
                        if (parts.size >= 4) {
                            val device = parts[0]
                            val mountPoint = parts[1]
                            val fileSystem = parts[2]
                            val options = parts[3]

                            // 修复只读检测逻辑
                            val isReadOnly = when {
                                options.contains("rw") -> false
                                options.contains("ro") -> true
                                else -> false
                            }

                            // 特殊处理：userdata 分区通常是读写的
                            val finalIsReadOnly = if (mountPoint == "/data" || device.contains("userdata")) {
                                false
                            } else {
                                isReadOnly
                            }

                            // 修复：处理 Magisk 镜像路径
                            var finalMountPoint = mountPoint
                            if (mountPoint.startsWith("/sbin/.magisk/mirror/")) {
                                val realPath = mountPoint.removePrefix("/sbin/.magisk/mirror")
                                if (realPath.isNotEmpty()) {
                                    finalMountPoint = realPath
                                }
                            }

                            // 提取实际设备名
                            val deviceName = getActualDeviceName(device, debugInfo)
                            if (deviceName.isNotEmpty()) {
                                mountInfo[deviceName] = MountInfo(
                                    device = device,
                                    mountPoint = finalMountPoint,
                                    fileSystem = fileSystem,
                                    isReadOnly = finalIsReadOnly
                                )
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mountInfo
    }

    /**
     * 从设备路径提取实际设备名
     */
    private fun getActualDeviceName(devicePath: String, debugInfo: StringBuilder): String {
        return try {
            val result = when {
                devicePath.startsWith("/dev/block/vold/public:") -> {
                    // 处理 vold 设备：直接从路径提取设备名
                    debugInfo.append("Found vold device in mounts: $devicePath\n")
                    "" // vold 设备在 mountInfo 中不需要映射
                }

                devicePath.startsWith("/dev/block/bootdevice/by-name/") -> {
                    // 解析 bootdevice by-name 符号链接指向的实际设备
                    resolveSymbolicLink(devicePath, "Bootdevice by-name", debugInfo)
                }

                devicePath.startsWith("/dev/block/platform/") -> {
                    // 解析平台目录的符号链接指向的实际设备
                    resolveSymbolicLink(devicePath, "Platform by-name", debugInfo)
                }

                devicePath.startsWith("/dev/block/sd") -> {
                    // 直接提取 sdX 设备名
                    devicePath.substringAfterLast('/')
                }

                devicePath.startsWith("/dev/block/dm-") -> {
                    // 直接提取 dm-X 设备名
                    devicePath.substringAfterLast('/')
                }

                devicePath.startsWith("/dev/block/by-name/") -> {
                    // 解析通用 by-name 目录的符号链接
                    resolveSymbolicLink(devicePath, "Generic by-name", debugInfo)
                }

                devicePath.startsWith("/dev/block/mapper/") -> {
                    // 解析 mapper 符号链接指向的实际设备
                    resolveSymbolicLink(devicePath, "Mapper", debugInfo)
                }

                devicePath.startsWith("/dev/block/mmcblk") -> {
                    // 直接提取 mmcblk 设备名
                    devicePath.substringAfterLast('/')
                }

                else -> {
                    devicePath.substringAfterLast('/')
                }
            }

            // 特别记录重要分区的解析结果
            if (devicePath.contains("userdata") || devicePath.contains("cache") ||
                devicePath.contains("system") || devicePath.contains("/data")) {
                debugInfo.append("Device name resolution: $devicePath -> $result\n")
            }

            result
        } catch (e: Exception) {
            if (devicePath.contains("userdata") || devicePath.contains("cache") ||
                devicePath.contains("system") || devicePath.contains("/data")) {
                debugInfo.append("ERROR getting actual device name for $devicePath: ${e.message}\n")
            }
            devicePath.substringAfterLast('/')
        }
    }

    /**
     * 解析符号链接的通用方法
     */
    private fun resolveSymbolicLink(devicePath: String, type: String, debugInfo: StringBuilder): String {
        val linkFile = File(devicePath)
        if (linkFile.exists()) {
            try {
                val canonicalPath = linkFile.canonicalPath
                val deviceName = File(canonicalPath).name
                if (devicePath.contains("userdata") || devicePath.contains("cache") || devicePath.contains("system")) {
                    debugInfo.append("$type: $devicePath -> $canonicalPath -> $deviceName\n")
                }
                return deviceName
            } catch (e: Exception) {
                debugInfo.append("ERROR resolving $type link $devicePath: ${e.message}\n")
                return devicePath.substringAfterLast('/')
            }
        } else {
            return devicePath.substringAfterLast('/')
        }
    }
}