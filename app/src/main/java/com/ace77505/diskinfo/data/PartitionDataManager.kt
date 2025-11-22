package com.ace77505.diskinfo.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlin.math.log10
import kotlin.math.pow

/**
 * 分区数据管理器
 * 负责从系统文件获取分区信息
 */
object PartitionDataManager {

    // 调试信息收集
    private val debugInfo = StringBuilder()

    /**
     * 获取所有分区信息
     */
    fun getPartitionInfo(context: Context): List<PartitionInfo> {
        debugInfo.clear()
        debugInfo.append("=== 分区信息调试日志 ===\n\n")

        return try {
            val partitions = mutableListOf<PartitionInfo>()

            // 获取分区名称映射
            val partitionNames = PartitionNameResolver.getPartitionNames(debugInfo)
            debugInfo.append("最终分区名称映射大小: ${partitionNames.size}\n")

            // 特别检查 OTG 设备的映射
            val otgDevices = listOf("sdh1", "sdh2", "sdh3", "sdh4", "sdh5")
            otgDevices.forEach { device ->
                if (partitionNames.containsKey(device)) {
                    debugInfo.append("OTG设备 $device 的显示名称: ${partitionNames[device]}\n")
                } else {
                    debugInfo.append("OTG设备 $device 未在映射表中找到\n")
                }
            }

            // 获取块设备列表（按 /proc/partitions 中的顺序）
            val orderedDeviceNames = PartitionOrderProvider.getOrderedBlockDevices(partitionNames, debugInfo)
            debugInfo.append("有序设备列表大小: ${orderedDeviceNames.size}\n")

            // 获取挂载信息
            val mountInfo = MountInfoReader.getMountInfo(debugInfo)

            // 获取 vold 管理的存储设备信息
            val voldDevices = VoldDeviceManager.getVoldDeviceInfo(debugInfo)

            // 获取分区详细信息，保持原始顺序
            orderedDeviceNames.forEach { deviceName ->
                // 特别记录OTG设备的处理
                if (deviceName.startsWith("sdh")) {
                    debugInfo.append("=== 正在处理OTG设备: $deviceName ===\n")
                }

                val partition = buildPartitionInfo(context, deviceName, mountInfo, voldDevices, partitionNames)
                if (partition != null) {
                    partitions.add(partition)
                    // 特别记录OTG设备的最终结果
                    if (deviceName.startsWith("sdh")) {
                        debugInfo.append("OTG设备 $deviceName 最终显示名称: ${partition.name}\n")
                    }
                }
            }

            // 添加 vold 设备中未在 partitions 中列出的设备
            addVoldOnlyDevices(context, partitions, voldDevices, partitionNames)

            // 复制调试信息到剪贴板
            copyDebugInfoToClipboard(context)

            partitions

        } catch (e: Exception) {
            debugInfo.append("ERROR: ${e.message}\n")
            e.printStackTrace()
            // 确保错误信息也被复制
            copyDebugInfoToClipboard(context)
            emptyList()
        }
    }

    /**
     * 复制调试信息到剪贴板
     */
    private fun copyDebugInfoToClipboard(context: Context) {
        try {
            // 检查设置，默认不复制
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val shouldCopy = prefs.getBoolean("default_copy_info", false)

            if (!shouldCopy) {
                debugInfo.append("\n=== 调试信息未复制到剪贴板（设置中已关闭默认复制） ===\n")
                return
            }

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("分区调试信息", debugInfo.toString())
            clipboard.setPrimaryClip(clip)
            debugInfo.append("\n=== 调试信息已复制到剪贴板 ===\n")
        } catch (e: Exception) {
            e.printStackTrace()
            debugInfo.append("\n=== 复制调试信息时出错: ${e.message} ===\n")
        }
    }
    /**
     * 构建分区信息 - 增强版本，支持 vold 设备
     */
    private fun buildPartitionInfo(
        context: Context,
        deviceName: String,
        mountInfo: Map<String, MountInfo>,
        voldDevices: Map<String, VoldDeviceInfo>,
        partitionNames: Map<String, String>
    ): PartitionInfo? {
        return try {
            val devicePath = "/dev/block/$deviceName"

            // 获取分区大小信息
            val size = PartitionSizeCalculator.getPartitionSize(deviceName) { devName, message ->
                addDebugInfo(devName, message)
            }
            addDebugInfo(deviceName, "Partition size: $size bytes")

            // 获取分区显示名称
            val displayName = partitionNames[deviceName] ?: deviceName
            addDebugInfo(deviceName, "Display name: $displayName (mapped from: $deviceName)")

            // 获取文件系统类型和挂载信息
            val mountInfoForDevice = mountInfo[deviceName]
            val isMounted = mountInfoForDevice != null
            val fileSystemType = mountInfoForDevice?.fileSystem ?: "unknown"
            val isReadOnly = mountInfoForDevice?.isReadOnly ?: false
            val mountPoint = mountInfoForDevice?.mountPoint ?: ""

            addDebugInfo(deviceName, "Mount info - mounted: $isMounted, fs: $fileSystemType, mountPoint: $mountPoint, readOnly: $isReadOnly")

            // 检查是否为 vold 设备
            val voldDeviceInfo = voldDevices[deviceName]
            val isVoldDevice = voldDeviceInfo != null
            val voldDevice = voldDeviceInfo?.voldDevice ?: ""
            addDebugInfo(deviceName, "Vold device: $isVoldDevice $voldDevice")

            // 获取空间使用信息
            val (usedSpace, availableSpace) = when {
                isMounted && mountPoint.isNotEmpty() -> {
                    val spaceInfo = SpaceInfoManager.getSpaceInfo(mountPoint)
                    addDebugInfo(deviceName, "Space info from mount: used=${spaceInfo.first}, available=${spaceInfo.second}")
                    spaceInfo
                }
                isVoldDevice -> {
                    val spaceInfo = VoldDeviceManager.getVoldDeviceSpaceInfo(context,
                        voldDeviceInfo, mountPoint) { devName, message ->
                        addDebugInfo(devName, message)
                    }
                    addDebugInfo(deviceName, "Space info from vold: used=${spaceInfo.first}, available=${spaceInfo.second}")
                    spaceInfo
                }
                else -> {
                    addDebugInfo(deviceName, "No space info available")
                    Pair(0L, 0L)
                }
            }

            // 计算使用百分比
            val usagePercentage = if ((isMounted || isVoldDevice) && usedSpace + availableSpace > 0) {
                ((usedSpace.toDouble() / (usedSpace + availableSpace).toDouble()) * 100).toInt()
            } else {
                0
            }
            addDebugInfo(deviceName, "Usage percentage: $usagePercentage%")

            // 处理 vold 设备的特殊逻辑
            var finalFileSystemType = fileSystemType
            var finalMountPoint = mountPoint

            if (isVoldDevice) {
                // 如果是 vold 设备但文件系统类型未知，尝试获取
                if (fileSystemType == "unknown") {
                    finalFileSystemType = VoldDeviceManager.getVoldFileSystemType(voldDeviceInfo.voldDevice) ?: "vold_managed"
                }

                // 如果是 vold 设备但挂载点为空，尝试查找
                if (mountPoint.isEmpty()) {
                    finalMountPoint = VoldDeviceManager.findVoldMountPoint(voldDeviceInfo.voldDevice) ?: ""
                }
            }

            addDebugInfo(deviceName, "Final fs type: $finalFileSystemType, final mount point: $finalMountPoint")

            // 确定是否真正挂载
            val finalIsMounted = isMounted || isVoldDevice

            // 构建基础分区信息
            val basicPartition = PartitionInfo(
                name = displayName,
                devicePath = devicePath,
                size = size,
                fileSystemSize = size,
                fileSystemOffset = 0L,
                fileSystemType = finalFileSystemType,
                mountPoint = finalMountPoint,
                isMounted = finalIsMounted,
                isReadOnly = isReadOnly,
                usedSpace = usedSpace,
                availableSpace = availableSpace,
                usagePercentage = usagePercentage
            )

            // 处理分区类型
            processPartitionType(basicPartition)

        } catch (e: Exception) {
            addDebugInfo(deviceName, "ERROR building partition info: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * 添加仅通过 vold 发现的设备
     */
    private fun addVoldOnlyDevices(
        context: Context,
        partitions: MutableList<PartitionInfo>,
        voldDevices: Map<String, VoldDeviceInfo>,
        partitionNames: Map<String, String>
    ) {
        try {
            val existingDevices = partitions.map { it.devicePath.substringAfterLast('/') }.toSet()

            voldDevices.forEach { (blockDevice, voldInfo) ->
                if (!existingDevices.contains(blockDevice)) {
                    addDebugInfo(blockDevice, "Adding vold-only device")
                    val partition = buildVoldOnlyPartitionInfo(context, voldInfo, partitionNames)
                    if (partition != null) {
                        partitions.add(partition)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 构建仅通过 vold 发现的分区信息
     */
    private fun buildVoldOnlyPartitionInfo(
        context: Context,
        voldInfo: VoldDeviceInfo,
        partitionNames: Map<String, String>
    ): PartitionInfo? {
        return try {
            val deviceName = voldInfo.blockDevice
            val devicePath = "/dev/block/$deviceName"

            // 获取分区大小
            val size = PartitionSizeCalculator.getPartitionSize(deviceName) { devName, message ->
                addDebugInfo(devName, message)
            }
            addDebugInfo(deviceName, "Vold-only partition size: $size")

            // 获取显示名称
            val displayName = partitionNames[deviceName] ?: "SD Card ${deviceName.substringAfterLast('p')}"
            addDebugInfo(deviceName, "Vold-only display name: $displayName")

            // 查找挂载信息
            var mountPoint = VoldDeviceManager.findVoldMountPoint(voldInfo.voldDevice) ?: ""
            val isMounted = mountPoint.isNotEmpty()
            addDebugInfo(deviceName, "Vold-only mount point: $mountPoint, mounted: $isMounted")

            // 获取文件系统类型
            var fileSystemType = VoldDeviceManager.getVoldFileSystemType(voldInfo.voldDevice) ?: "vold_managed"
            addDebugInfo(deviceName, "Vold-only filesystem: $fileSystemType")

            // 获取空间信息
            val (usedSpace, availableSpace) = if (isMounted) {
                val spaceInfo = SpaceInfoManager.getSpaceInfo(mountPoint)
                addDebugInfo(deviceName, "Vold-only space info: used=${spaceInfo.first}, available=${spaceInfo.second}")
                spaceInfo
            } else {
                // 即使没有传统挂载点，也尝试通过 StorageManager 获取
                val spaceInfo = VoldDeviceManager.getVoldDeviceSpaceInfo(context, voldInfo, mountPoint) { devName, message ->
                    addDebugInfo(devName, message)
                }
                if (spaceInfo.first > 0 || spaceInfo.second > 0) {
                    addDebugInfo(deviceName, "Vold-only space info from StorageManager: used=${spaceInfo.first}, available=${spaceInfo.second}")
                    spaceInfo
                } else {
                    addDebugInfo(deviceName, "Vold-only: no space info (not mounted)")
                    Pair(0L, 0L)
                }
            }

            // 计算使用百分比
            val usagePercentage = if (isMounted && usedSpace + availableSpace > 0) {
                ((usedSpace.toDouble() / (usedSpace + availableSpace).toDouble()) * 100).toInt()
            } else {
                0
            }
            addDebugInfo(deviceName, "Vold-only usage: $usagePercentage%")

            // 构建基础分区信息
            val basicPartition = PartitionInfo(
                name = displayName,
                devicePath = devicePath,
                size = size,
                fileSystemSize = size,
                fileSystemOffset = 0L,
                fileSystemType = fileSystemType,
                mountPoint = mountPoint,
                isMounted = isMounted,
                isReadOnly = false,
                usedSpace = usedSpace,
                availableSpace = availableSpace,
                usagePercentage = usagePercentage
            )

            // 处理分区类型
            processPartitionType(basicPartition)

        } catch (e: Exception) {
            addDebugInfo(voldInfo.blockDevice, "ERROR building vold-only partition: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * 添加调试信息（只针对 mmcblk1pX 设备）
     */
    private fun addDebugInfo(deviceName: String, message: String) {
        if (deviceName.startsWith("mmcblk1p")) {
            debugInfo.append("[$deviceName] $message\n")
        }
    }

    /**
     * 格式化字节大小为可读字符串
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

        val size = bytes / 1024.0.pow(digitGroups.toDouble())

        return when (digitGroups) {
            3 -> "%.2f %s".format(size, units[digitGroups]) // GB 单位精确到0.01
            else -> "%.1f %s".format(size, units[digitGroups.coerceAtMost(4)])
        }
    }

    /**
     * 预处理分区类型
     */
    fun processPartitionType(partition: PartitionInfo): PartitionInfo {
        return PartitionTypeManager.processPartitionType(partition)
    }
}