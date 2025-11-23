package com.ace77505.diskinfo.data

import com.google.gson.Gson

/**
 * 分区类型枚举 - 简化版本
 */
enum class PartitionType {
    LOOP,       // Loop 设备
    SUPER,      // Super 分区
    DEFAULT     // 默认分区（移除 EXTERNAL 类型）
}

/**
 * 分区信息数据类
 */
data class PartitionInfo(
    val name: String,
    val devicePath: String,
    val size: Long,
    val fileSystemSize: Long,
    val fileSystemOffset: Long,
    val fileSystemType: String,
    val mountPoint: String,
    val isMounted: Boolean,
    val isReadOnly: Boolean,
    val usedSpace: Long,
    val availableSpace: Long,
    val usagePercentage: Int,
    val partitionType: PartitionType = PartitionType.DEFAULT
) {
    val totalSpace: Long
        get() = usedSpace + availableSpace

    // JSON 序列化方法
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): PartitionInfo = Gson().fromJson(json, PartitionInfo::class.java)
    }
}