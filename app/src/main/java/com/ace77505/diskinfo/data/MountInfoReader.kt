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
                                // 如果明确包含 "rw"，则是读写
                                options.contains("rw") -> false
                                // 如果明确包含 "ro"，则是只读
                                options.contains("ro") -> true
                                // 默认情况下，大多数分区应该是读写的
                                else -> false
                            }

                            // 特殊处理：userdata 分区通常是读写的
                            val finalIsReadOnly = if (mountPoint == "/data" || device.contains("userdata")) {
                                false // userdata 分区强制设为读写
                            } else {
                                isReadOnly
                            }

                            // 提取实际的设备名称
                            val deviceName = getActualDeviceName(device, debugInfo)
                            if (deviceName.isNotEmpty()) {
                                mountInfo[deviceName] = MountInfo(
                                    device = device,
                                    mountPoint = mountPoint,
                                    fileSystem = fileSystem,
                                    isReadOnly = finalIsReadOnly
                                )
                                // 特别记录重要分区的挂载信息
                                if (deviceName == "mmcblk0p75" || deviceName == "mmcblk0p77" || deviceName == "mmcblk0p79") {
                                    debugInfo.append("Mount info for $deviceName: $mountPoint, $fileSystem, readOnly: $finalIsReadOnly\n")
                                }
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
     * 从设备路径提取实际的设备名称
     */
    private fun getActualDeviceName(devicePath: String, debugInfo: StringBuilder): String {
        return try {
            val result = when {
                devicePath.startsWith("/dev/block/vold/public:") -> {
                    // 处理 vold 设备：直接从路径提取设备名
                    debugInfo.append("Found vold device in mounts: $devicePath\n")
                    "" // 返回空字符串，不添加到 mountInfo
                }

                devicePath.startsWith("/dev/block/bootdevice/by-name/") -> {
                    // 解析 bootdevice by-name 符号链接指向的实际设备
                    val linkFile = File(devicePath)
                    if (linkFile.exists()) {
                        try {
                            val canonicalPath = linkFile.canonicalPath
                            val deviceName = File(canonicalPath).name
                            if (devicePath.contains("userdata") || devicePath.contains("cache") || devicePath.contains("system")) {
                                debugInfo.append("Bootdevice by-name: $devicePath -> $canonicalPath -> $deviceName\n")
                            }
                            deviceName
                        } catch (e: Exception) {
                            debugInfo.append("ERROR resolving bootdevice link $devicePath: ${e.message}\n")
                            devicePath.substringAfterLast('/')
                        }
                    } else {
                        devicePath.substringAfterLast('/')
                    }
                }

                devicePath.startsWith("/dev/block/platform/") -> {
                    // 解析平台目录的符号链接指向的实际设备
                    val linkFile = File(devicePath)
                    if (linkFile.exists()) {
                        try {
                            val canonicalPath = linkFile.canonicalPath
                            val deviceName = File(canonicalPath).name
                            if (devicePath.contains("userdata") || devicePath.contains("cache") || devicePath.contains("system")) {
                                debugInfo.append("Platform by-name: $devicePath -> $canonicalPath -> $deviceName\n")
                            }
                            deviceName
                        } catch (e: Exception) {
                            debugInfo.append("ERROR resolving platform link $devicePath: ${e.message}\n")
                            devicePath.substringAfterLast('/')
                        }
                    } else {
                        devicePath.substringAfterLast('/')
                    }
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
                    val linkFile = File(devicePath)
                    if (linkFile.exists()) {
                        try {
                            File(linkFile.canonicalPath).name
                        } catch (_: Exception) {
                            devicePath.substringAfterLast('/')
                        }
                    } else {
                        devicePath.substringAfterLast('/')
                    }
                }

                devicePath.startsWith("/dev/block/mapper/") -> {
                    // 解析 mapper 符号链接指向的实际设备
                    val linkFile = File(devicePath)
                    if (linkFile.exists()) {
                        try {
                            File(linkFile.canonicalPath).name
                        } catch (_: Exception) {
                            devicePath.substringAfterLast('/')
                        }
                    } else {
                        devicePath.substringAfterLast('/')
                    }
                }

                else -> {
                    devicePath.substringAfterLast('/')
                }
            }

            result
        } catch (e: Exception) {
            if (devicePath.contains("userdata") || devicePath.contains("cache") || devicePath.contains("system")) {
                debugInfo.append("ERROR getting actual device name for $devicePath: ${e.message}\n")
            }
            devicePath.substringAfterLast('/')
        }
    }
}