package com.ace77505.diskinfo.data

import java.util.Date

data class ImportExportRecord(
    val id: Long = 0,
    val type: String, // "import" 或 "export"
    val fileName: String,
    val filePath: String,
    val timestamp: Date,
    val success: Boolean = true,
    val message: String = ""
) {
    companion object {
        const val TYPE_IMPORT = "import"
        const val TYPE_EXPORT = "export"
    }
}