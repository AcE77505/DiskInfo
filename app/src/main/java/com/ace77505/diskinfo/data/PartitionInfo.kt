package com.ace77505.diskinfo.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 分区类型枚举 - 简化版本
 */
enum class PartitionType {
    @SerializedName("LOOP")
    LOOP,       // Loop 设备

    @SerializedName("SUPER")
    SUPER,      // Super 分区

    @SerializedName("DEFAULT")
    DEFAULT     // 默认分区
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
        fun fromJson(json: String): PartitionInfo? = try {
            Gson().fromJson(json, PartitionInfo::class.java)
        } catch (_: Exception) {
            null
        }
    }
}