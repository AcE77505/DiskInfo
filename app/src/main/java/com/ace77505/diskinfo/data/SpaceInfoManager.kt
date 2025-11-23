package com.ace77505.diskinfo.data

import android.os.StatFs
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 空间信息管理器
 */
object SpaceInfoManager {

    /**
     * 获取空间使用信息
     */
    fun getSpaceInfo(mountPoint: String): Pair<Long, Long> {
        return try {
            // 主要方法：使用 StatFs
            getSpaceInfoWithStatFs(mountPoint)
        } catch (_: Exception) {
            // 备用方法：尝试其他方式
            getSpaceInfoWithFallback(mountPoint)
        }
    }/**
     * 使用 StatFs 获取空间信息
     */
    private fun getSpaceInfoWithStatFs(mountPoint: String): Pair<Long, Long> {
        return try {
            val statFs = StatFs(mountPoint)

            // 检查 StatFs 是否有效
            if (statFs.blockCountLong == 0L) {
                throw Exception("StatFs returned zero block count")
            }

            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong

            val totalSpace = totalBlocks * blockSize
            val availableSpace = availableBlocks * blockSize
            val usedSpace = totalSpace - availableSpace

            // 验证结果是否合理
            if (totalSpace <= 0 || availableSpace < 0 || usedSpace < 0) {
                throw Exception("Invalid space values: total=$totalSpace, available=$availableSpace, used=$usedSpace")
            }

            Pair(usedSpace, availableSpace)
        } catch (e: Exception) {
            // 如果 StatFs 失败，抛出异常让备用方法处理
            throw e
        }
    }

    /**
     * 备用方法获取空间信息
     */
    private fun getSpaceInfoWithFallback(mountPoint: String): Pair<Long, Long> {
        return try {
            // 方法1：尝试通过 Runtime.exec 执行 df 命令
            val process = Runtime.getRuntime().exec("df $mountPoint")
            val inputStream = process.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            var usedSpace = 0L
            var availableSpace = 0L

            // 跳过第一行（标题）
            reader.readLine()

            // 解析第二行（数据行）
            val line = reader.readLine()
            if (line != null && line.contains(mountPoint)) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    // 通常格式：文件系统 1K-块 已用 可用 已用% 挂载点
                    // parts[2] 是已用块数，parts[3] 是可用块数（1K块）
                    val usedBlocks = parts[2].toLongOrNull() ?: 0L
                    val availableBlocks = parts[3].toLongOrNull() ?: 0L

                    usedSpace = usedBlocks * 1024  // 转换为字节
                    availableSpace = availableBlocks * 1024  // 转换为字节
                }
            }

            reader.close()
            process.waitFor()

            if (usedSpace > 0 || availableSpace > 0) {
                Pair(usedSpace, availableSpace)
            } else {
                // 方法2：返回默认值
                Pair(0L, 0L)
            }
        } catch (_: Exception) {
            // 所有方法都失败，返回默认值
            Pair(0L, 0L)
        }
    }
}