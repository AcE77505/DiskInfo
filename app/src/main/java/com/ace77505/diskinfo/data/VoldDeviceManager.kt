package com.ace77505.diskinfo.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.regex.Pattern

/**
 * Vold 设备管理器 - 增强版本
 */
object VoldDeviceManager {

    private const val VOLD_DIR = "/dev/block/vold"
    private const val STORAGE_DIR = "/storage"
    private const val PROC_MOUNTS = "/proc/mounts"
    private const val PROC_PARTITIONS = "/proc/partitions"

    /**
     * 获取 vold 管理的设备信息
     */
    fun getVoldDeviceInfo(debugInfo: StringBuilder): Map<String, VoldDeviceInfo> {
        val voldDevices = mutableMapOf<String, VoldDeviceInfo>()

        try {
            val voldDir = File(VOLD_DIR)
            if (voldDir.exists() && voldDir.isDirectory) {
                voldDir.listFiles()?.forEach { file ->
                    try {
                        val fileName = file.name
                        if (fileName.startsWith("public:")) {
                            // 解析 public:major:minor 或 public:major,minor 格式
                            val parts = fileName.split("[:,]") // 支持冒号和逗号分隔
                            if (parts.size >= 3) {
                                val major = parts[1].toIntOrNull()
                                val minor = parts[2].toIntOrNull()

                                if (major != null && minor != null) {
                                    // 查找对应的块设备
                                    val blockDevice = findBlockDeviceByMajorMinor(major, minor, debugInfo)
                                    if (blockDevice != null) {
                                        voldDevices[blockDevice] = VoldDeviceInfo(
                                            voldDevice = fileName,
                                            major = major,
                                            minor = minor,
                                            blockDevice = blockDevice
                                        )
                                        addDebugInfo(blockDevice, "Found vold device: $fileName", debugInfo)
                                    } else {
                                        // 如果找不到对应的块设备，记录调试信息
                                        debugInfo.append("Vold device $fileName has no corresponding block device (major=$major, minor=$minor)\n")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // 忽略错误
                    }
                }
            } else {
                debugInfo.append("Vold directory does not exist: $VOLD_DIR\n")
            }
        } catch (e: Exception) {
            debugInfo.append("ERROR accessing vold directory: ${e.message}\n")
            e.printStackTrace()
        }

        // 同时从 /proc/mounts 中查找 vold 设备
        findVoldDevicesFromMounts(voldDevices, debugInfo)

        return voldDevices
    }
    private fun findVoldDevicesFromMounts(voldDevices: MutableMap<String, VoldDeviceInfo>, debugInfo: StringBuilder) {
        try {
            BufferedReader(FileReader(PROC_MOUNTS)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { mountLine ->
                        if (mountLine.contains("/dev/block/vold/public:")) {
                            val parts = mountLine.split(" ")
                            if (parts.size >= 4) {
                                val voldDevicePath = parts[0] // 如 /dev/block/vold/public:179,1 或 /dev/block/vold/public:179:1
                                val mountPoint = parts[1]

                                // 提取 vold 设备名
                                val voldDeviceName = voldDevicePath.substringAfterLast('/')

                                if (voldDeviceName.contains("179")) { // 只记录主要设备
                                    debugInfo.append("Found vold mount: $voldDeviceName -> $mountPoint\n")
                                }
                                // 尝试通过设备号查找对应的块设备
                                if (voldDeviceName.startsWith("public:")) {
                                    // 支持两种格式：public:179,1 和 public:179:1
                                    val deviceNameWithoutPrefix = voldDeviceName.removePrefix("public:")
                                    val separator = if (deviceNameWithoutPrefix.contains(',')) ',' else ':'
                                    val numberParts = deviceNameWithoutPrefix.split(separator)

                                    if (numberParts.size >= 2) {
                                        val major = numberParts[0].toIntOrNull()
                                        val minor = numberParts[1].toIntOrNull()

                                        debugInfo.append("Parsed vold device: major=$major, minor=$minor (separator=$separator)\n")

                                        if (major != null && minor != null) {
                                            val blockDevice = findBlockDeviceByMajorMinor(major, minor, debugInfo)
                                            if (blockDevice != null) {
                                                voldDevices[blockDevice] = VoldDeviceInfo(
                                                    voldDevice = voldDeviceName,
                                                    major = major,
                                                    minor = minor,
                                                    blockDevice = blockDevice
                                                )
                                                debugInfo.append("Mapped vold device $voldDeviceName to block device: $blockDevice\n")
                                            } else {
                                                debugInfo.append("Could not find block device for vold: $voldDeviceName (major=$major, minor=$minor)\n")
                                            }
                                        } else {
                                            debugInfo.append("Invalid major/minor in vold device: $voldDeviceName\n")
                                        }
                                    } else {
                                        debugInfo.append("Malformed vold device name: $voldDeviceName\n")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            debugInfo.append("ERROR reading vold devices from mounts: ${e.message}\n")
            e.printStackTrace()
        }
    }
    /**
     * 根据 major 和 minor 号查找对应的块设备
     */
    private fun findBlockDeviceByMajorMinor(major: Int, minor: Int, debugInfo: StringBuilder): String? {
        return try {
            var foundDevice: String? = null

            debugInfo.append("Searching for block device with major=$major, minor=$minor\n")

            // 方法1：从 /proc/partitions 查找
            BufferedReader(FileReader(PROC_PARTITIONS)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        val parts = it.trim().split(Pattern.compile("\\s+"))
                        if (parts.size >= 4) {
                            val partMajor = parts[0].toIntOrNull()
                            val partMinor = parts[1].toIntOrNull()
                            val deviceName = parts[3]

                            if (partMajor == major && partMinor == minor && isValidPartition(deviceName)) {
                                foundDevice = deviceName
                                debugInfo.append("Found by major/minor in partitions: $major:$minor -> $deviceName\n")
                                return@use // 找到就退出
                            }
                        }
                    }
                }
            }

            if (foundDevice == null) {
                debugInfo.append("Device not found in /proc/partitions for major=$major, minor=$minor\n")
            }

            // 方法2：从 /sys/dev/block 查找（如果方法1没找到）
            if (foundDevice == null) {
                foundDevice = findBlockDeviceFromSys(major, minor, debugInfo)
            }

            foundDevice
        } catch (_: Exception) {
            debugInfo.append("ERROR in findBlockDeviceByMajorMinor\n")
            null
        }
    }
    private fun findBlockDeviceFromSys(major: Int, minor: Int, debugInfo: StringBuilder): String? {
        return try {
            val devBlockDir = File("/sys/dev/block/$major:$minor")
            if (devBlockDir.exists() && devBlockDir.isDirectory) {
                // 查找 subsystem 链接
                val subsystemLink = File(devBlockDir, "subsystem")
                if (subsystemLink.exists()) {
                    val subsystem = File(subsystemLink.canonicalPath).name
                    if (subsystem == "block") {
                        // 这是块设备，获取设备名
                        val deviceName = devBlockDir.name
                        debugInfo.append("Found device from sys: $deviceName (major=$major, minor=$minor)\n")
                        return deviceName
                    }
                }

                // 或者直接查找 uevent 文件
                val ueventFile = File(devBlockDir, "uevent")
                if (ueventFile.exists()) {
                    BufferedReader(FileReader(ueventFile)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line?.startsWith("DEVNAME=") == true) {
                                val deviceName = line.substringAfter("DEVNAME=")
                                debugInfo.append("Found device from uevent: $deviceName (major=$major, minor=$minor)\n")
                                return deviceName
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            debugInfo.append("ERROR finding device from sys: ${e.message}\n")
            null
        }
    }

    /**
     * 获取 vold 设备的空间信息 - 增强版本
     */
    fun getVoldDeviceSpaceInfo(
        context: Context,
        voldDevice: VoldDeviceInfo,
        existingMountPoint: String,
        addDebugInfo: (String, String) -> Unit
    ): Pair<Long, Long> {
        return try {
            // 方法1: 使用 StorageManager 获取用户可访问的路径
            val storageManagerSpaceInfo = getSpaceInfoFromStorageManager(context, voldDevice, addDebugInfo)
            if (storageManagerSpaceInfo.first > 0 || storageManagerSpaceInfo.second > 0) {
                return storageManagerSpaceInfo
            }

            // 方法2: 尝试用户可访问的 /storage 路径
            val storagePath = findUserAccessibleStoragePath(voldDevice, addDebugInfo)
            if (storagePath != null) {
                addDebugInfo(voldDevice.blockDevice, "Using user-accessible storage path: $storagePath")
                val result = SpaceInfoManager.getSpaceInfo(storagePath)
                if (result.first > 0 || result.second > 0) {
                    return result
                }
            }

            // 方法3: 如果已有挂载点，尝试使用（可能权限不足）
            if (existingMountPoint.isNotEmpty()) {
                addDebugInfo(voldDevice.blockDevice, "Trying existing mount point: $existingMountPoint")
                val result = SpaceInfoManager.getSpaceInfo(existingMountPoint)
                if (result.first > 0 || result.second > 0) {
                    return result
                }
            }

            // 方法4: 尝试通过 vold 设备名查找挂载点
            val mountPoint = findVoldMountPoint(voldDevice.voldDevice)
            if (mountPoint != null && mountPoint.isNotEmpty()) {
                addDebugInfo(voldDevice.blockDevice, "Trying vold mount point: $mountPoint")
                val result = SpaceInfoManager.getSpaceInfo(mountPoint)
                if (result.first > 0 || result.second > 0) {
                    return result
                }

                // 方法5: 如果 SpaceInfoManager 失败，使用 df 命令
                addDebugInfo(voldDevice.blockDevice, "SpaceInfoManager failed, trying df command for: $mountPoint")
                val dfResult = getSpaceInfoFromDf(mountPoint) { _, message ->
                    addDebugInfo(voldDevice.blockDevice, message)
                }
                if (dfResult.first > 0 || dfResult.second > 0) {
                    return dfResult
                }
            }

            addDebugInfo(voldDevice.blockDevice, "No accessible space info found")
            Pair(0L, 0L)
        } catch (e: Exception) {
            addDebugInfo(voldDevice.blockDevice, "ERROR getting vold space info: ${e.message}")
            e.printStackTrace()
            Pair(0L, 0L)
        }
    }
    /**
     * 使用 StorageManager 获取空间信息 - 这是关键改进
     */
    @SuppressLint("UsableSpace")
    private fun getSpaceInfoFromStorageManager(
        context: Context,
        voldDevice: VoldDeviceInfo,
        addDebugInfo: (String, String) -> Unit
    ): Pair<Long, Long> {
        return try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val storageVolumes = storageManager.storageVolumes

                for (volume in storageVolumes) {
                    try {
                        val isRemovable = volume.isRemovable

                        if (isRemovable) {
                            // 这是可移动存储设备
                            val storagePath = getStorageVolumePath(volume)
                            if (storagePath != null) {
                                val storageDir = File(storagePath)
                                if (storageDir.exists()) {
                                    val state = Environment.getExternalStorageState(storageDir)
                                    if (state == Environment.MEDIA_MOUNTED) {
                                        addDebugInfo(voldDevice.blockDevice, "Found removable storage via StorageManager: $storagePath")

                                        val totalSpace = storageDir.totalSpace
                                        val freeSpace = storageDir.freeSpace
                                        val usedSpace = totalSpace - freeSpace

                                        addDebugInfo(voldDevice.blockDevice, "StorageManager space - total: $totalSpace, used: $usedSpace, free: $freeSpace")

                                        return Pair(usedSpace, freeSpace)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        addDebugInfo(voldDevice.blockDevice, "Error processing storage volume: ${e.message}")
                    }
                }
            } else {
                // Android 7.0 以下使用旧方法
                getSpaceInfoFromStorageManagerLegacy(context, voldDevice, addDebugInfo)
            }

            Pair(0L, 0L)
        } catch (e: Exception) {
            addDebugInfo(voldDevice.blockDevice, "StorageManager access failed: ${e.message}")
            Pair(0L, 0L)
        }
    }

    @Suppress("DEPRECATION")
    private fun getSpaceInfoFromStorageManagerLegacy(
        context: Context,
        voldDevice: VoldDeviceInfo,
        addDebugInfo: (String, String) -> Unit
    ): Pair<Long, Long> {
        return try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

            val getVolumeListMethod: Method = storageManager.javaClass.getMethod("getVolumeList")
            val storageVolumes = getVolumeListMethod.invoke(storageManager) as Array<*>

            for (volume in storageVolumes) {
                if (volume != null) {
                    try {
                        val isRemovableMethod: Method = volume.javaClass.getMethod("isRemovable")
                        val isRemovable = isRemovableMethod.invoke(volume) as Boolean

                        if (isRemovable) {
                            val getPathMethod: Method = volume.javaClass.getMethod("getPath")
                            val path = getPathMethod.invoke(volume) as String

                            val storageDir = File(path)
                            if (storageDir.exists()) {
                                val state = Environment.getExternalStorageState(storageDir)
                                if (state == Environment.MEDIA_MOUNTED) {
                                    addDebugInfo(voldDevice.blockDevice, "Found removable storage via legacy StorageManager: $path")

                                    val totalSpace = storageDir.totalSpace
                                    val freeSpace = storageDir.freeSpace
                                    val usedSpace = totalSpace - freeSpace

                                    return Pair(usedSpace, freeSpace)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // 继续处理下一个卷
                    }
                }
            }

            Pair(0L, 0L)
        } catch (e: Exception) {
            addDebugInfo(voldDevice.blockDevice, "Legacy StorageManager access failed: ${e.message}")
            Pair(0L, 0L)
        }
    }

    /**
     * 获取 StorageVolume 的路径
     */
    private fun getStorageVolumePath(volume: android.os.storage.StorageVolume): String? {
        return try {
            // 方法1: 尝试使用 getDirectory (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val getDirectoryMethod = volume.javaClass.getMethod("getDirectory")
                    val directory = getDirectoryMethod.invoke(volume) as File?
                    directory?.absolutePath
                } catch (_: Exception) {
                    null
                }
            } else null
        } catch (_: Exception) {
            null
        } ?: try {
            // 方法2: 尝试使用 getPath (通过反射)
            val getPathMethod = volume.javaClass.getMethod("getPath")
            getPathMethod.invoke(volume) as String
        } catch (_: Exception) {
            null
        } ?: try {
            // 方法3: 尝试使用 getPathFile (某些设备)
            val getPathFileMethod = volume.javaClass.getMethod("getPathFile")
            val pathFile = getPathFileMethod.invoke(volume) as File?
            pathFile?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("UsableSpace")
    private fun findUserAccessibleStoragePath(voldDevice: VoldDeviceInfo, addDebugInfo: (String, String) -> Unit): String? {
        return try {
            val storageDir = File(STORAGE_DIR)
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        // 匹配类似 "9A58-0A02" 或 "ED27-E605" 的目录名
                        if (file.name.matches(Regex("^[0-9A-F]{4}-[0-9A-F]{4}$"))) {
                            // 检查这个目录是否可访问且有空间信息
                            try {
                                val totalSpace = file.totalSpace
                                val usableSpace = file.usableSpace
                                if (totalSpace > 0 && usableSpace > 0) {
                                    addDebugInfo(voldDevice.blockDevice, "Found accessible storage: ${file.absolutePath}, total=$totalSpace, usable=$usableSpace")
                                    return file.absolutePath
                                }
                            } catch (e: Exception) {
                                addDebugInfo(voldDevice.blockDevice, "Cannot access storage ${file.absolutePath}: ${e.message}")
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            addDebugInfo(voldDevice.blockDevice, "ERROR finding storage path: ${e.message}")
            null
        }
    }

    /**
     * 查找 vold 设备的挂载点
     */
    fun findVoldMountPoint(voldDevice: String): String? {
        return try {
            BufferedReader(FileReader(PROC_MOUNTS)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { mountLine ->
                        if (mountLine.contains(voldDevice)) {
                            val parts = mountLine.split(" ")
                            if (parts.size >= 2) {
                                return parts[1]
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取 vold 设备的文件系统类型
     */
    fun getVoldFileSystemType(voldDevice: String): String? {
        return try {
            BufferedReader(FileReader(PROC_MOUNTS)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { mountLine ->
                        if (mountLine.contains(voldDevice)) {
                            val parts = mountLine.split(" ")
                            if (parts.size >= 3) {
                                return parts[2]
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun addDebugInfo(deviceName: String, message: String, debugInfo: StringBuilder) {
        if (deviceName.startsWith("mmcblk1p")) {
            debugInfo.append("[$deviceName] $message\n")
        }
    }

    private fun isValidPartition(deviceName: String): Boolean {
        return !deviceName.startsWith("ram")
    }
    private fun getSpaceInfoFromDf(mountPoint: String, addDebugInfo: (String, String) -> Unit): Pair<Long, Long> {
        return try {
            val process = Runtime.getRuntime().exec("df -k $mountPoint")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            var headerSkipped = false

            while (reader.readLine().also { line = it } != null) {
                if (!headerSkipped) {
                    headerSkipped = true
                    continue
                }

                line?.let { dfLine ->
                    val parts = dfLine.trim().split(Regex("\\s+"))
                    if (parts.size >= 6 && parts[5] == mountPoint) {
                        val total1KBlocks = parts[1].toLongOrNull() ?: 0L
                        val used1KBlocks = parts[2].toLongOrNull() ?: 0L
                        val available1KBlocks = parts[3].toLongOrNull() ?: 0L

                        val totalBytes = total1KBlocks * 1024
                        val usedBytes = used1KBlocks * 1024
                        val availableBytes = available1KBlocks * 1024

                        addDebugInfo("df", "DF result for $mountPoint: total=$totalBytes, used=$usedBytes, available=$availableBytes")

                        return Pair(usedBytes, availableBytes)
                    }
                }
            }

            addDebugInfo("df", "DF command failed to find mount point: $mountPoint")
            Pair(0L, 0L)
        } catch (e: Exception) {
            addDebugInfo("df", "ERROR running df command: ${e.message}")
            Pair(0L, 0L)
        }
    }

}