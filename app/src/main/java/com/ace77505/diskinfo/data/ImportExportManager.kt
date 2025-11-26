// ImportExportManager.kt
package com.ace77505.diskinfo.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.text.*
import java.util.*
import java.io.*

object ImportExportManager {

    private const val EXPORT_FILE_PREFIX = "diskinfo"
    private const val FILE_EXTENSION = ".json"

    // 导出分区信息
    suspend fun exportPartitions(
        context: Context,
        partitions: List<PartitionInfo>,
        outputUri: Uri
    ): ImportExportRecord {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    exportToStream(partitions, outputStream)
                }

                val fileName = getFileNameFromUri(contentResolver, outputUri) ?: generateExportFileName()

                ImportExportRecord(
                    type = ImportExportRecord.TYPE_EXPORT,
                    fileName = fileName,
                    filePath = outputUri.toString(),
                    timestamp = Date(),
                    success = true,
                    message = "成功导出 ${partitions.size} 个分区"
                )
            } catch (e: Exception) {
                ImportExportRecord(
                    type = ImportExportRecord.TYPE_EXPORT,
                    fileName = "unknown",
                    filePath = outputUri.toString(),
                    timestamp = Date(),
                    success = false,
                    message = "导出失败: ${e.message}"
                )
            }
        }
    }

    // 导入分区信息
    suspend fun importPartitions(
        context: Context,
        inputUri: Uri
    ): Triple<List<PartitionInfo>?, String?, ImportExportRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val (partitions, exportTime) = contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    // 读取整个文件内容
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    parseJsonString(jsonString)
                } ?: Pair(null, null)

                val fileName = getFileNameFromUri(contentResolver, inputUri) ?: "unknown"

                val record = ImportExportRecord(
                    type = ImportExportRecord.TYPE_IMPORT,
                    fileName = fileName,
                    filePath = inputUri.toString(),
                    timestamp = Date(),
                    success = partitions != null,
                    message = if (partitions != null) "成功导入 ${partitions.size} 个分区" else "导入失败: 文件格式错误"
                )

                Triple(partitions, exportTime, record)
            } catch (e: Exception) {
                val record = ImportExportRecord(
                    type = ImportExportRecord.TYPE_IMPORT,
                    fileName = "unknown",
                    filePath = inputUri.toString(),
                    timestamp = Date(),
                    success = false,
                    message = "导入失败: ${e.message}"
                )
                Triple(null, null, record)
            }
        }
    }

    private fun exportToStream(partitions: List<PartitionInfo>, outputStream: OutputStream) {
        JsonWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
            writer.beginObject()
            writer.name("export_info").apply {
                beginObject()
                name("export_time").value(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                name("total_partitions").value(partitions.size)
                endObject()
            }
            writer.name("partitions").apply {
                beginArray()
                partitions.forEach { partition ->
                    // 使用 Gson 将每个分区对象转换为 JSON
                    val partitionJson = partition.toJson()
                    writer.value(partitionJson)
                }
                endArray()
            }
            writer.endObject()
        }
    }

    private fun parseJsonString(jsonString: String): Pair<List<PartitionInfo>?, String?> {
        return try {
            val gson = Gson()
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

            var exportTime: String? = null

            // 提取导出时间
            if (jsonObject.has("export_info")) {
                val exportInfo = jsonObject.getAsJsonObject("export_info")
                if (exportInfo.has("export_time")) {
                    exportTime = exportInfo.get("export_time").asString
                }
            }

            val partitions = if (jsonObject.has("partitions")) {
                val partitionsArray = jsonObject.getAsJsonArray("partitions")
                val partitionList = mutableListOf<PartitionInfo>()

                partitionsArray.forEach { element ->
                    val partitionJson = element.asString
                    val partition = PartitionInfo.fromJson(partitionJson)
                    if (partition != null) {
                        partitionList.add(partition)
                    }
                }

                partitionList.ifEmpty { null }
            } else {
                null
            }

            Pair(partitions, exportTime)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    private fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex("_display_name")
                    if (displayNameIndex != -1) {
                        cursor.getString(displayNameIndex)
                    } else {
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

    private fun generateExportFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${EXPORT_FILE_PREFIX}_$timestamp$FILE_EXTENSION"
    }
    // 在 ImportExportManager.kt 中修复 importPartitionsWithLongTime 方法
    suspend fun importPartitionsWithLongTime(
        context: Context,
        inputUri: Uri
    ): Triple<List<PartitionInfo>?, Long, ImportExportRecord> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查是否是文件路径（不是 content URI）
                val uriString = inputUri.toString()
                val (partitions, exportTimeString, record) = if (uriString.startsWith("/")) {
                    // 这是文件路径，使用文件方式读取
                    val file = File(uriString)
                    if (file.exists()) {
                        val inputStream = FileInputStream(file)
                        val jsonString = inputStream.bufferedReader().use { it.readText() }
                        inputStream.close()

                        val (parsedPartitions, parsedExportTime) = parseJsonString(jsonString)
                        val importRecord = ImportExportRecord(
                            type = ImportExportRecord.TYPE_IMPORT,
                            fileName = file.name,
                            filePath = uriString,
                            timestamp = Date(),
                            success = parsedPartitions != null,
                            message = if (parsedPartitions != null) "成功导入 ${parsedPartitions.size} 个分区" else "导入失败: 文件格式错误"
                        )
                        Triple(parsedPartitions, parsedExportTime, importRecord)
                    } else {
                        Triple(null, null, ImportExportRecord(
                            type = ImportExportRecord.TYPE_IMPORT,
                            fileName = "unknown",
                            filePath = uriString,
                            timestamp = Date(),
                            success = false,
                            message = "文件不存在"
                        ))
                    }
                } else {
                    // 这是 content URI，使用原有逻辑
                    importPartitions(context, inputUri)
                }

                // 将字符串时间转换为 Long 时间戳
                val exportTimeLong = if (exportTimeString != null) {
                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val date = dateFormat.parse(exportTimeString)
                        date?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                } else {
                    0L
                }

                Triple(partitions, exportTimeLong, record)
            } catch (e: Exception) {
                val record = ImportExportRecord(
                    type = ImportExportRecord.TYPE_IMPORT,
                    fileName = "unknown",
                    filePath = inputUri.toString(),
                    timestamp = Date(),
                    success = false,
                    message = "导入失败: ${e.message}"
                )
                Triple(null, 0L, record)
            }
        }
    }
    // 添加从文件导入的方法
    suspend fun importPartitionsFromFile(
        filePath: String
    ): Triple<List<PartitionInfo>?, Long, ImportExportRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    val record = ImportExportRecord(
                        type = ImportExportRecord.TYPE_IMPORT,
                        fileName = file.name,
                        filePath = filePath,
                        timestamp = Date(),
                        success = false,
                        message = "文件不存在"
                    )
                    return@withContext Triple(null, 0L, record)
                }

                val inputStream = FileInputStream(file)
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                // 解析 JSON 字符串
                val (partitions, exportTimeString) = parseJsonString(jsonString)

                // 转换时间格式
                val exportTimeLong = if (exportTimeString != null) {
                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val date = dateFormat.parse(exportTimeString)
                        date?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                } else {
                    0L
                }

                val record = ImportExportRecord(
                    type = ImportExportRecord.TYPE_IMPORT,
                    fileName = file.name,
                    filePath = filePath,
                    timestamp = Date(),
                    success = partitions != null,
                    message = if (partitions != null) "成功导入 ${partitions.size} 个分区" else "导入失败: 文件格式错误"
                )

                Triple(partitions, exportTimeLong, record)
            } catch (e: Exception) {
                val record = ImportExportRecord(
                    type = ImportExportRecord.TYPE_IMPORT,
                    fileName = "unknown",
                    filePath = filePath,
                    timestamp = Date(),
                    success = false,
                    message = "导入失败: ${e.message}"
                )
                Triple(null, 0L, record)
            }
        }
    }
}