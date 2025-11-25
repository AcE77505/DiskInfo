package com.ace77505.diskinfo

import android.app.Application
import com.ace77505.diskinfo.data.PartitionInfo

class DiskInfoApplication : Application() {
    var currentPartitions: List<PartitionInfo>? = null
}