package com.ace77505.diskinfo.data

import java.io.File

/**
 * 分区名称解析器 - 简化版本
 */
object PartitionNameResolver {

    private const val BOOTDEVICE_BY_NAME = "/dev/block/platform/bootdevice/by-name"
    private const val BLOCK_MAPPER = "/dev/block/mapper"
    private const val PLATFORM_DIR = "/dev/block/platform"

    /**
     * 获取分区名称映射 - 简化版本
     */
    fun getPartitionNames(debugInfo: StringBuilder): Map<String, String> {
        val partitionNames = mutableMapOf<String, String>()

        try {
            debugInfo.append("=== 开始分区名称映射 ===\n")

            // 1. 获取基本的分区名称
            val basicNames = getBasicPartitionNames(debugInfo)
            partitionNames.putAll(basicNames)
            // 不记录基本分区数，减少调试信息冗余

            // 2. 从通用 by-name 目录读取
            addGenericByNamePartitions(partitionNames, debugInfo)

            // 3. 从 mapper 目录读取所有符号链接
            addMapperPartitions(partitionNames)

        } catch (e: Exception) {
            debugInfo.append("ERROR in getPartitionNames: ${e.message}\n")
            e.printStackTrace()
        }

        return partitionNames
    }

    /**
     * 获取基本分区名称 - 简化版本
     */
    private fun getBasicPartitionNames(debugInfo: StringBuilder): Map<String, String> {
        val names = mutableMapOf<String, String>()

        try {
            // 1. 优先检查 bootdevice 符号链接
            val bootDeviceByNameDir = File(BOOTDEVICE_BY_NAME)
            if (bootDeviceByNameDir.exists() && bootDeviceByNameDir.isDirectory) {
                debugInfo.append("Found bootdevice by-name directory\n")
                bootDeviceByNameDir.listFiles()?.forEach { file ->
                    try {
                        val target = File(file.canonicalPath).name
                        names[target] = file.name
                    } catch (_: Exception) {
                        // 忽略错误
                    }
                }
            }

            // 2. 如果没有 bootdevice，通过平台目录识别
            val platformDir = File(PLATFORM_DIR)
            if (platformDir.exists() && platformDir.isDirectory) {
                // 查找所有 by-name 目录
                val allByNameDirs = findByNameDirectoriesRecursive(platformDir, debugInfo)

                debugInfo.append("找到 ${allByNameDirs.size} 个 by-name 目录\n")
                allByNameDirs.forEach { dir ->
                    processByNameDirectory(dir, names, debugInfo)
                }
            }

        } catch (e: Exception) {
            debugInfo.append("ERROR getting basic partition names: ${e.message}\n")
            e.printStackTrace()
        }

        return names
    }

    /**
     * 处理 by-name 目录
     */
    private fun processByNameDirectory(byNameDir: File, names: MutableMap<String, String>, debugInfo: StringBuilder) {
        try {
            byNameDir.listFiles()?.forEach { file ->
                try {
                    val targetPath = readSymbolicLink(file)
                    if (targetPath != null) {
                        val target = File(targetPath).name
                        val linkName = file.name
                        names[target] = linkName
                    } else {
                        // 如果 readlink 失败，尝试备用方法
                        try {
                            val canonicalFile = File(file.canonicalPath)
                            val target = canonicalFile.name
                            val linkName = file.name
                            names[target] = linkName
                        } catch (e: Exception) {
                            debugInfo.append("错误解析符号链接 ${file.absolutePath}: ${e.message}\n")
                        }
                    }
                } catch (e: Exception) {
                    debugInfo.append("错误处理符号链接 ${file.absolutePath}: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            debugInfo.append("ERROR processing by-name directory: ${e.message}\n")
        }
    }

    /**
     * 添加通用 by-name 分区
     */
    private fun addGenericByNamePartitions(partitionNames: MutableMap<String, String>, debugInfo: StringBuilder) {
        val byNameDir = File("/dev/block/by-name")
        if (byNameDir.exists() && byNameDir.isDirectory) {
            byNameDir.listFiles()?.forEach { file ->
                try {
                    val target = File(file.canonicalPath).name
                    partitionNames[target] = file.name
                } catch (_: Exception) {
                    // 忽略错误
                }
            }
        } else {
            debugInfo.append("Generic by-name directory not found: /dev/block/by-name\n")
        }
    }

    /**
     * 添加 mapper 分区
     */
    private fun addMapperPartitions(partitionNames: MutableMap<String, String>) {
        val mapperDir = File(BLOCK_MAPPER)
        if (mapperDir.exists() && mapperDir.isDirectory) {
            mapperDir.listFiles()?.forEach { file ->
                try {
                    val target = File(file.canonicalPath).name
                    partitionNames[target] = file.name
                } catch (_: Exception) {
                    // 忽略错误
                }
            }
        }
    }

    /**
     * 递归查找所有包含 by-name 目录的路径
     */
    private fun findByNameDirectoriesRecursive(rootDir: File, debugInfo: StringBuilder): List<File> {
        val byNameDirs = mutableListOf<File>()

        try {
            if (rootDir.exists() && rootDir.isDirectory) {
                rootDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        if (file.name == "by-name") {
                            byNameDirs.add(file)
                        } else {
                            // 递归搜索子目录，但跳过一些不必要的深层目录
                            if (!shouldSkipDirectory(file)) {
                                byNameDirs.addAll(findByNameDirectoriesRecursive(file, debugInfo))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            debugInfo.append("ERROR searching by-name directories: ${e.message}\n")
        }

        return byNameDirs
    }

    /**
     * 判断是否应该跳过目录（优化性能）
     */
    private fun shouldSkipDirectory(dir: File): Boolean {
        val skipPatterns = listOf(
            "mapper", "vold", "ram", "loop", "dm-"
        )
        return skipPatterns.any { pattern -> dir.name.contains(pattern) }
    }

    /**
     * 使用 readlink 系统调用来解析符号链接
     */
    private fun readSymbolicLink(file: File): String? {
        return try {
            // 方法1：使用 File 的 canonicalPath（兼容性好）
            val canonicalPath = file.canonicalPath
            val absolutePath = file.absolutePath

            if (canonicalPath != absolutePath) {
                // 如果是符号链接，canonicalPath 会指向实际目标
                canonicalPath
            } else {
                // 方法2：对于 API 26+ 使用 NIO
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        val path = java.nio.file.Paths.get(file.absolutePath)
                        java.nio.file.Files.readSymbolicLink(path).toString()
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // 移除以下不再需要的方法：
    // - getExternalStoragePartitionNames()
    // - findUSBDevicesDirectly()
    // - addAllUSBDevices()
    // - isActualUSBDevice()
    // - identifyInternalStorageByNameDir()
    // - containsMmcblkDevice()
    // - 以及其他内外置存储识别相关的方法
}