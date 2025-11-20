package com.ace77505.diskinfo.data

import android.os.StatFs

/**
 * 空间信息管理器
 */
object SpaceInfoManager {

    /**
     * 获取空间使用信息
     */
    fun getSpaceInfo(mountPoint: String): Pair<Long, Long> {
        return try {
            val statFs = StatFs(mountPoint)

            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong

            val totalSpace = totalBlocks * blockSize
            val availableSpace = availableBlocks * blockSize
            val usedSpace = totalSpace - availableSpace

            Pair(usedSpace, availableSpace)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }
}