package com.ace77505.diskinfo.data

data class VoldDeviceInfo(
    val voldDevice: String,  // 如 "public:179:1"
    val major: Int,          // 主设备号
    val minor: Int,          // 次设备号
    val blockDevice: String  // 对应的块设备名，如 "mmcblk1p1"
)

data class MountInfo(
    val device: String,
    val mountPoint: String,
    val fileSystem: String,
    val isReadOnly: Boolean
)