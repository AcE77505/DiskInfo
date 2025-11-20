package com.ace77505.diskinfo.data

/**
 * 分区类型管理器
 */
object PartitionTypeManager {

    /**
     * 预处理分区类型
     */
    fun processPartitionType(partition: PartitionInfo): PartitionInfo {
        return partition.copy(
            partitionType = determinePartitionType(partition)
        )
    }

    /**
     * 判断分区类型 - 简化版本，只保留 LOOP 和 SUPER 类型
     */
    private fun determinePartitionType(partition: PartitionInfo): PartitionType {
        return when {
            // loop 设备判断逻辑
            partition.name.contains("loop", ignoreCase = true) ||
                    partition.devicePath.contains("loop", ignoreCase = true) -> PartitionType.LOOP

            // super 分区判断逻辑
            partition.name.contains("super", ignoreCase = true) ||
                    partition.devicePath.contains("super", ignoreCase = true) ||
                    partition.name.startsWith("dm-") ||
                    partition.devicePath.contains("dm-") -> PartitionType.SUPER

            else -> PartitionType.DEFAULT
        }
    }
}