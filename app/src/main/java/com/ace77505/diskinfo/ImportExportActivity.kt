package com.ace77505.diskinfo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace77505.diskinfo.data.ImportExportManager
import com.ace77505.diskinfo.data.ImportExportRecord
import com.ace77505.diskinfo.data.PartitionInfo
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*

class ImportExportActivity : AppCompatActivity() {

    private lateinit var adapter: ImportExportRecordAdapter
    private val records = mutableListOf<ImportExportRecord>()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson
    companion object {
        const val EXTRA_IMPORT_FILE_URI = "import_file_uri"
        const val EXTRA_IMPORT_FILE_NAME = "import_file_name"
        const val EXTRA_EXPORT_TIME = "export_time"
        const val PREFS_NAME = "import_export_history"
        const val KEY_RECORDS = "import_export_records"
        const val MAX_RECORDS = 50 // 最多保存50条记录
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            CoroutineScope(Dispatchers.Main).launch {
                val currentPartitions = getCurrentPartitionsFromMainActivity()
                if (currentPartitions != null) {
                    val record = ImportExportManager.exportPartitions(
                        this@ImportExportActivity,
                        currentPartitions,
                        it
                    )
                    addRecord(record)
                    showMessage("导出完成: ${record.fileName}")
                    // 导出后不需要设置结果，直接显示成功消息
                } else {
                    showMessage("无法获取分区数据")
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        handleImportResult(uri)
    }

    private fun handleImportResult(uri: android.net.Uri?) {
        uri?.let {
            // 检查文件扩展名
            val fileName = getFileName(uri)
            if (fileName != null && !fileName.endsWith(".json", ignoreCase = true)) {
                showMessage("请选择 JSON 文件 (.json)")
                return@let
            }

            // 如果是有效的 JSON 文件，继续执行导入逻辑
            CoroutineScope(Dispatchers.Main).launch {
                // 检查是否应该保存导入文件
                val shouldSaveFile = getSharedPreferences("app_settings", MODE_PRIVATE)
                    .getBoolean("save_import_files", true)

                val (partitions, exportTime, record) = if (shouldSaveFile) {
                    // 保存文件到私有存储并使用文件路径导入
                    val filePath = saveImportFileToPrivateStorage(uri, fileName ?: "unknown_file.json")
                    ImportExportManager.importPartitionsFromFile(this@ImportExportActivity, filePath)
                } else {
                    // 直接使用 URI 导入
                    ImportExportManager.importPartitionsWithLongTime(this@ImportExportActivity, uri)
                }

                addRecord(record)

                if (record.success && partitions != null) {
                    showMessage("导入成功: ${record.fileName}")
                    setResult(RESULT_OK, Intent().apply {
                        putExtra(EXTRA_IMPORT_FILE_URI, record.filePath)
                        putExtra(EXTRA_IMPORT_FILE_NAME, record.fileName)
                        putExtra(EXTRA_EXPORT_TIME, exportTime)
                    })
                    finish()
                } else {
                    // 显示导入失败弹窗
                    showImportErrorDialog(record.message)
                }
            }
        }
    }

    /**
     * 保存导入文件到私有存储
     */
    private suspend fun saveImportFileToPrivateStorage(uri: android.net.Uri, fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.use { input ->
                    // 创建导入目录
                    val filesDir = filesDir
                    val importDir = File(filesDir, "imports")
                    if (!importDir.exists()) {
                        importDir.mkdirs()
                    }

                    val outputFile = File(importDir, fileName)
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }

                    outputFile.absolutePath
                } ?: uri.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                uri.toString()
            }
        }
    }

    // 获取文件名的方法
    private fun getFileName(uri: android.net.Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                var fileName: String? = null
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = cursor.getString(displayNameIndex)
                        }
                    }
                }
                fileName
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_export)

        println("=== ImportExportActivity onCreate ===")

        // 初始化 Gson
        gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()

        // 使用应用级别的 SharedPreferences，确保数据持久化
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 调试：检查当前数据状态
        debugSharedPreferences("onCreate开始")

        setupToolbar()
        setupRecyclerView()
        loadHistory()

        debugSharedPreferences("onCreate结束")
    }

    /**
     * 调试 SharedPreferences 状态
     */
    private fun debugSharedPreferences(tag: String) {
        try {
            val allData = sharedPreferences.all
            println("=== SharedPreferences 调试 ($tag) ===")
            println("文件: $PREFS_NAME")
            println("所有键: ${allData.keys}")

            val recordsData = sharedPreferences.getString(KEY_RECORDS, null)
            println("KEY_RECORDS 存在: ${sharedPreferences.contains(KEY_RECORDS)}")
            println("KEY_RECORDS 数据: $recordsData")
            println("KEY_RECORDS 数据长度: ${recordsData?.length ?: 0}")
            println("内存中记录数量: ${records.size}")
            println("=================================")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun setupToolbar() {
        // 设置返回按钮点击事件
        findViewById<ImageButton>(R.id.action_back).setOnClickListener {
            finish()
        }

        // 设置导出按钮点击事件
        findViewById<ImageButton>(R.id.action_export).setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
            val fileName = "partition_info_$timestamp.json"
            exportLauncher.launch(fileName)
        }

        // 设置导入按钮点击事件 - 根据 Android 版本使用不同策略
        findViewById<ImageButton>(R.id.action_import).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用原有逻辑，直接筛选 JSON 文件
                importLauncher.launch(arrayOf("application/json"))
            } else {
                // Android 10 以下版本，允许选择任意文件，但会检查后缀名
                importLauncher.launch(arrayOf("*/*"))
            }
        }

        // 添加长按清空历史记录功能
        findViewById<TextView>   (R.id.toolbar_title).setOnLongClickListener {
            showClearHistoryDialog()
            true
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recordsRecyclerView)
        adapter = ImportExportRecordAdapter(records)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    /**
     * 加载历史记录
     */
    private fun loadHistory() {
        try {
            println("开始加载历史记录...")
            val recordsJson = sharedPreferences.getString(KEY_RECORDS, null)
            println("从 SharedPreferences 读取的记录JSON: $recordsJson")

            if (recordsJson != null && recordsJson.isNotEmpty() && recordsJson != "[]") {
                // 清理可能的转义字符
                val cleanJson = recordsJson
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")

                println("清理后的JSON: $cleanJson")

                if (cleanJson.trim().startsWith("[") && cleanJson.trim().endsWith("]")) {
                    // 修复：使用更安全的方式创建TypeToken
                    val type: Type = TypeToken.getParameterized(List::class.java, ImportExportRecord::class.java).type
                    val savedRecords = gson.fromJson<List<ImportExportRecord>>(cleanJson, type)

                    println("解析出的记录数量: ${savedRecords?.size ?: 0}")

                    if (savedRecords != null && savedRecords.isNotEmpty()) {
                        records.clear()
                        records.addAll(savedRecords)
                        adapter.notifyDataSetChanged()
                        println("成功加载 ${records.size} 条记录")
                        showMessage("加载了 ${records.size} 条历史记录")
                    } else {
                        println("解析记录失败或记录为空")
                        // 保持现有记录不变
                    }
                } else {
                    println("数据格式错误，不是有效的 JSON 数组")
                    // 保持现有记录不变
                }
            } else {
                println("没有找到保存的记录或记录为空")
                // 保持现有记录不变
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("加载历史记录异常: ${e.message}")
            showMessage("加载历史记录失败，但保留现有数据")
            // 异常时保持现有记录不变
        }
    }

    private fun addRecord(record: ImportExportRecord) {
        // 检查是否已存在相同记录（避免重复）
        val existingIndex = records.indexOfFirst {
            it.fileName == record.fileName &&
                    it.type == record.type &&
                    it.timestamp == record.timestamp
        }

        if (existingIndex == -1) {
            records.add(0, record) // 新的记录添加到顶部

            // 限制记录数量，避免过多
            if (records.size > MAX_RECORDS) {
                records.removeAt(records.size - 1)
            }

            adapter.notifyItemInserted(0)
            val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recordsRecyclerView)
            recyclerView.scrollToPosition(0)

            // 保存到历史记录
            saveRecordToHistory()
        } else {
            // 如果记录已存在，更新它
            records[existingIndex] = record
            adapter.notifyItemChanged(existingIndex)
            saveRecordToHistory()
        }
    }


    /**
     * 保存记录到历史记录
     */
    private fun saveRecordToHistory() {
        try {
            if (records.isNotEmpty()) {
                val recordsJson = gson.toJson(records)
                println("准备保存 ${records.size} 条记录")

                // 使用 commit() 而不是 apply() 确保立即保存
                val success = sharedPreferences.edit()
                    .putString(KEY_RECORDS, recordsJson)
                    .commit()  // 使用 commit() 确保同步保存

                if (success) {
                    println("保存记录成功")

                    // 验证保存的数据
                    val savedData = sharedPreferences.getString(KEY_RECORDS, null)
                    println("验证保存的数据长度: ${savedData?.length}")
                } else {
                    println("保存记录失败")
                }
            } else {
                println("没有记录需要保存")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("保存记录失败: ${e.message}")
        }
    }


    private fun validateAndFixSharedPreferences() {
        try {
            // 检查是否存在错误格式的数据
            val allPrefs = sharedPreferences.all
            println("SharedPreferences 中的所有键: ${allPrefs.keys}")

            // 检查我们的键是否存在
            if (sharedPreferences.contains(KEY_RECORDS)) {
                val recordsData = sharedPreferences.getString(KEY_RECORDS, null)
                println("KEY_RECORDS 数据类型: ${recordsData?.javaClass?.simpleName}")
                println("KEY_RECORDS 数据内容: $recordsData")

                // 如果数据包含 HTML 转义字符，修复它
                if (recordsData != null && recordsData.contains("&quot;")) {
                    println("检测到需要修复的数据格式")
                    val fixedData = recordsData
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")

                    sharedPreferences.edit()
                        .putString(KEY_RECORDS, fixedData)
                        .apply()
                    println("数据修复完成")
                }
            } else {
                println("KEY_RECORDS 不存在，将创建新数据")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("数据验证失败: ${e.message}")
        }
    }
    /**
     * 显示清空历史记录对话框
     */
    private fun showClearHistoryDialog() {
        if (records.isEmpty()) {
            showMessage("没有历史记录可清空")
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清空历史记录")
            .setMessage("确定要清空所有导入导出记录吗？此操作不可恢复。")
            .setPositiveButton("清空") { dialog, _ ->
                clearHistory()
                showMessage("历史记录已清空")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 清空历史记录
     */
    private fun clearHistory() {
        records.clear()
        adapter.notifyDataSetChanged()
        saveRecordToHistory()
    }

    /**
     * 显示导入失败弹窗
     */
    private fun showImportErrorDialog(errorMessage: String) {
        val dialogMessage = "导入失败: $errorMessage"

        // 复制错误信息到剪贴板
        copyToClipboard(dialogMessage)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入失败")
            .setMessage(dialogMessage)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("复制错误信息") { dialog, _ ->
                // 再次复制以确保用户知道已复制
                copyToClipboard(dialogMessage)
                showMessage("错误信息已复制到剪贴板")
                dialog.dismiss()
            }
            .setOnDismissListener {
                // 对话框关闭时显示提示
                showMessage("错误信息已自动复制到剪贴板")
            }
            .show()
    }

    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("导入错误信息", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果剪贴板复制失败，静默处理
        }
    }

    /**
     * 显示消息（用于弹窗中的提示）
     */
    private fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun getCurrentPartitionsFromMainActivity(): List<PartitionInfo>? {
        // 通过 Application 类获取当前分区数据
        return (application as? DiskInfoApplication)?.currentPartitions
    }
}