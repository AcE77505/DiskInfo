package com.ace77505.diskinfo.data

import android.os.Parcel
import android.os.Parcelable

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
    val name: String,                    // 分区名称
    val devicePath: String,              // 设备路径
    val size: Long,                      // 分区大小
    val fileSystemSize: Long,            // 文件系统大小
    val fileSystemOffset: Long,          // 文件系统起始位置
    val fileSystemType: String,          // 文件系统类型
    val mountPoint: String,              // 挂载点
    val isMounted: Boolean,              // 是否挂载
    val isReadOnly: Boolean,             // 是否只读
    val usedSpace: Long,                 // 已用空间
    val availableSpace: Long,            // 可用空间
    val usagePercentage: Int,            // 占用百分比
    // 简化分区类型
    val partitionType: PartitionType = PartitionType.DEFAULT
) : Parcelable {
    val totalSpace: Long
        get() = usedSpace + availableSpace

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(devicePath)
        dest.writeLong(size)
        dest.writeLong(fileSystemSize)
        dest.writeLong(fileSystemOffset)
        dest.writeString(fileSystemType)
        dest.writeString(mountPoint)
        dest.writeByte(if (isMounted) 1 else 0)
        dest.writeByte(if (isReadOnly) 1 else 0)
        dest.writeLong(usedSpace)
        dest.writeLong(availableSpace)
        dest.writeInt(usagePercentage)
        dest.writeInt(partitionType.ordinal)
    }

    companion object CREATOR : Parcelable.Creator<PartitionInfo> {
        override fun createFromParcel(parcel: Parcel): PartitionInfo {
            return PartitionInfo(
                name = parcel.readString() ?: "",
                devicePath = parcel.readString() ?: "",
                size = parcel.readLong(),
                fileSystemSize = parcel.readLong(),
                fileSystemOffset = parcel.readLong(),
                fileSystemType = parcel.readString() ?: "",
                mountPoint = parcel.readString() ?: "",
                isMounted = parcel.readByte() != 0.toByte(),
                isReadOnly = parcel.readByte() != 0.toByte(),
                usedSpace = parcel.readLong(),
                availableSpace = parcel.readLong(),
                usagePercentage = parcel.readInt(),
                partitionType = PartitionType.entries[parcel.readInt()]
            )
        }

        override fun newArray(size: Int): Array<PartitionInfo?> {
            return arrayOfNulls(size)
        }
    }
}