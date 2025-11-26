package com.ace77505.diskinfo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView

    // 选择模式相关变量
    private lateinit var normalButtonContainer: LinearLayout
    private lateinit var selectionButtonContainer: LinearLayout
    private lateinit var exitSelectionButton: ImageButton
    private lateinit var deleteSelectedButton: ImageButton
    private lateinit var toolbarTitle: TextView

    // imports 目录
    private val importsDir: File by lazy {
        File(filesDir, "imports").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    companion object {
        const val EXTRA_IMPORT_FILE_URI = "import_file_uri"
        const val EXTRA_IMPORT_FILE_NAME = "import_file_name"
        const val EXTRA_EXPORT_TIME = "export_time"
        const val PREFS_NAME = "import_export_history"
        const val KEY_RECORDS = "import_export_records"
        const val MAX_RECORDS = 50
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

            CoroutineScope(Dispatchers.Main).launch {
                // 检查是否应该保存导入文件
                val shouldSaveFile = getSharedPreferences("app_settings", MODE_PRIVATE)
                    .getBoolean("save_import_files", true)

                val (partitions, exportTime, record) = if (shouldSaveFile) {
                    // 保存文件到私有存储并使用文件路径导入
                    val filePath = saveImportFileToPrivateStorage(uri, fileName ?: "unknown_file.json")
                    ImportExportManager.importPartitionsFromFile(filePath)
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
    private suspend fun saveImportFileToPrivateStorage(uri: android.net.Uri, fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.use { input ->
                    val outputFile = File(importsDir, fileName)
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

        gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        debugSharedPreferences("onCreate开始")

        setupToolbar()
        setupRecyclerView()
        setupSelectionMode()
        loadHistory()

        debugSharedPreferences("onCreate结束")
    }

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
        toolbarTitle = findViewById(R.id.toolbar_title)

        findViewById<ImageButton>(R.id.action_back).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.action_export).setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
            val fileName = "partition_info_$timestamp.json"
            exportLauncher.launch(fileName)
        }

        findViewById<ImageButton>(R.id.action_import).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                importLauncher.launch(arrayOf("application/json"))
            } else {
                importLauncher.launch(arrayOf("*/*"))
            }
        }

        toolbarTitle.setOnLongClickListener {
            showClearHistoryDialog()
            true
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recordsRecyclerView)
        adapter = ImportExportRecordAdapter(records)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSelectionMode() {
        normalButtonContainer = findViewById(R.id.normal_button_container)
        selectionButtonContainer = findViewById(R.id.selection_button_container)
        exitSelectionButton = findViewById(R.id.action_exit_selection)
        deleteSelectedButton = findViewById(R.id.action_delete_selected)

        adapter.setOnSelectionModeChangeListener { isSelectionMode ->
            if (isSelectionMode) {
                normalButtonContainer.visibility = View.GONE
                selectionButtonContainer.visibility = View.VISIBLE
                updateSelectionTitle()
            } else {
                normalButtonContainer.visibility = View.VISIBLE
                selectionButtonContainer.visibility = View.GONE
                toolbarTitle.text = "导入/导出记录"
            }
        }

        adapter.setOnItemLongClickListener { position ->
            adapter.enterSelectionMode(position)
            true
        }

        adapter.setOnSelectionChangeListener { _ ->
            updateSelectionTitle()
        }

        exitSelectionButton.setOnClickListener {
            adapter.exitSelectionMode()
        }

        deleteSelectedButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun updateSelectionTitle() {
        val selectedCount = adapter.getSelectedItems().size
        toolbarTitle.text = "已选择 $selectedCount 项"
    }

    private fun showDeleteConfirmationDialog() {
        val selectedCount = adapter.getSelectedItems().size
        if (selectedCount == 0) {
            showMessage("请先选择要删除的记录")
            return
        }

        val selectedRecords = adapter.getSelectedRecords()

        val hasImportRecords = selectedRecords.any { it.type == ImportExportRecord.TYPE_IMPORT }
        val importRecordsCount = selectedRecords.count { it.type == ImportExportRecord.TYPE_IMPORT }

        val message = StringBuilder()
        message.append("确定要删除以下 $selectedCount 条记录吗？\n\n")

        selectedRecords.take(3).forEach { record ->
            val type = if (record.type == ImportExportRecord.TYPE_IMPORT) "导入" else "导出"
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(record.timestamp)
            message.append("• $type - ${record.fileName} ($time)\n")
        }

        if (selectedCount > 3) {
            message.append("• ... 还有 ${selectedCount - 3} 条记录\n")
        }

        message.append("\n此操作不可恢复。")

        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除记录")
            .setPositiveButton("删除") { dialog, _ ->
                performDeleteSelectedItems()
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }

        if (hasImportRecords) {
            val warningText = "警告：删除操作会同时删除 $importRecordsCount 个导入文件！"
            val fullMessage = "${message}\n\n$warningText"

            val spannable = android.text.SpannableString(fullMessage)
            val warningStart = fullMessage.indexOf(warningText)
            val warningEnd = warningStart + warningText.length

            val gentleRed = ContextCompat.getColor(this, android.R.color.holo_red_light)

            spannable.setSpan(
                android.text.style.ForegroundColorSpan(gentleRed),
                warningStart,
                warningEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                warningStart,
                warningEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            dialogBuilder.setMessage(spannable)
        } else {
            dialogBuilder.setMessage(message.toString())
        }

        dialogBuilder.show()
    }

    private fun performDeleteSelectedItems() {
        val selectedRecords = adapter.getSelectedRecords()
        val selectedPositions = adapter.getSelectedItems().toList()
        if (selectedPositions.isEmpty()) return

        val hasImportRecords = selectedRecords.any { it.type == ImportExportRecord.TYPE_IMPORT }

        val sortedPositions = selectedPositions.sortedDescending()

        var filesDeleted = 0
        if (hasImportRecords) {
            selectedRecords.forEach { record ->
                if (deleteImportFile(record)) {
                    filesDeleted++
                }
            }
        }

        sortedPositions.forEach { position ->
            if (position < records.size) {
                records.removeAt(position)
            }
        }

        adapter.updateData(ArrayList(records))
        saveRecordToHistory()
        adapter.exitSelectionMode()

        val deletedCount = sortedPositions.size
        val message = if (hasImportRecords && filesDeleted > 0) {
            "已删除 $deletedCount 条记录和 $filesDeleted 个导入文件"
        } else {
            "已删除 $deletedCount 条记录"
        }
        showMessage(message)
    }

    private fun deleteImportFile(record: ImportExportRecord): Boolean {
        return try {
            if (record.type == ImportExportRecord.TYPE_IMPORT &&
                record.filePath.contains("/imports/")) {
                val file = File(record.filePath)
                if (file.exists()) {
                    file.delete()
                } else {
                    true
                }
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun clearImportsDirectory(): Boolean {
        return try {
            if (importsDir.exists() && importsDir.isDirectory) {
                var success = true
                importsDir.listFiles()?.forEach { file ->
                    if (!file.delete()) {
                        success = false
                    }
                }
                success
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadHistory() {
        try {
            println("开始加载历史记录...")
            val recordsJson = sharedPreferences.getString(KEY_RECORDS, null)
            println("从 SharedPreferences 读取的记录JSON: $recordsJson")

            if (recordsJson != null && recordsJson.isNotEmpty() && recordsJson != "[]") {
                val cleanJson = recordsJson
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")

                println("清理后的JSON: $cleanJson")

                if (cleanJson.trim().startsWith("[") && cleanJson.trim().endsWith("]")) {
                    val type: Type = TypeToken.getParameterized(List::class.java, ImportExportRecord::class.java).type
                    val savedRecords = gson.fromJson<List<ImportExportRecord>>(cleanJson, type)

                    println("解析出的记录数量: ${savedRecords?.size ?: 0}")

                    if (savedRecords != null && savedRecords.isNotEmpty()) {
                        records.clear()
                        records.addAll(savedRecords)
                        adapter.updateData(ArrayList(records))
                        println("成功加载 ${records.size} 条记录")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("加载历史记录异常: ${e.message}")
            showMessage("加载历史记录失败")
        }
    }

    private fun addRecord(record: ImportExportRecord) {
        val existingIndex = records.indexOfFirst { existingRecord ->
            existingRecord.fileName == record.fileName &&
                    existingRecord.type == record.type
        }

        if (existingIndex == -1) {
            records.add(0, record)

            if (records.size > MAX_RECORDS) {
                records.removeAt(records.size - 1)
            }

            adapter.updateData(ArrayList(records))
            recyclerView.scrollToPosition(0)
            saveRecordToHistory()
            showMessage("已添加新记录: ${record.fileName}")
        } else {
            val updatedRecord = records[existingIndex].copy(
                timestamp = record.timestamp,
                message = record.message,
                success = record.success
            )
            records[existingIndex] = updatedRecord

            if (existingIndex > 0) {
                records.removeAt(existingIndex)
                records.add(0, updatedRecord)
                adapter.updateData(ArrayList(records))
                recyclerView.scrollToPosition(0)
            } else {
                adapter.notifyItemChanged(0)
            }

            saveRecordToHistory()
            showMessage("已更新记录: ${record.fileName}")
        }
    }

    private fun saveRecordToHistory() {
        try {
            val recordsJson = gson.toJson(records)
            println("准备保存 ${records.size} 条记录")

            val success = sharedPreferences.edit()
                .putString(KEY_RECORDS, recordsJson)
                .commit()

            if (success) {
                println("保存记录成功")
            } else {
                println("保存记录失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("保存记录失败: ${e.message}")
        }
    }

    private fun showClearHistoryDialog() {
        if (records.isEmpty()) {
            showMessage("没有历史记录可清空")
            return
        }

        val hasImportRecords = records.any { it.type == ImportExportRecord.TYPE_IMPORT }
        val importRecordsCount = records.count { it.type == ImportExportRecord.TYPE_IMPORT }

        val baseMessage = "确定要清空所有 ${records.size} 条导入导出记录吗？\n\n此操作不可恢复。"

        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清空历史记录")
            .setPositiveButton("清空") { dialog, _ ->
                performClearHistory()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)

        if (hasImportRecords) {
            val warningText = "警告：清空操作会同时删除 $importRecordsCount 个导入文件！"
            val fullMessage = "$baseMessage\n\n$warningText"

            val spannable = android.text.SpannableString(fullMessage)
            val warningStart = fullMessage.indexOf(warningText)
            val warningEnd = warningStart + warningText.length

            val gentleRed = ContextCompat.getColor(this, android.R.color.holo_red_light)

            spannable.setSpan(
                android.text.style.ForegroundColorSpan(gentleRed),
                warningStart,
                warningEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                warningStart,
                warningEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            dialogBuilder.setMessage(spannable)
        } else {
            dialogBuilder.setMessage(baseMessage)
        }

        dialogBuilder.show()
    }

    private fun performClearHistory() {
        val deletedCount = records.size

        val hasImportRecords = records.any { it.type == ImportExportRecord.TYPE_IMPORT }
        val importRecordsCount = records.count { it.type == ImportExportRecord.TYPE_IMPORT }

        var filesDeleted = 0
        if (hasImportRecords) {
            if (clearImportsDirectory()) {
                filesDeleted = importRecordsCount
            }
        }

        records.clear()
        adapter.updateData(ArrayList(records))
        saveRecordToHistory()

        val message = if (hasImportRecords && filesDeleted > 0) {
            "已清空 $deletedCount 条记录和 $filesDeleted 个导入文件"
        } else {
            "已清空 $deletedCount 条记录"
        }
        showMessage(message)
    }

    private fun showImportErrorDialog(errorMessage: String) {
        val dialogMessage = "导入失败: $errorMessage"

        copyToClipboard(dialogMessage)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入失败")
            .setMessage(dialogMessage)
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("复制错误信息") { dialog, _ ->
                copyToClipboard(dialogMessage)
                showMessage("错误信息已复制到剪贴板")
                dialog.dismiss()
            }
            .setOnDismissListener {
                showMessage("错误信息已自动复制到剪贴板")
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("导入错误信息", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun getCurrentPartitionsFromMainActivity(): List<PartitionInfo>? {
        return (application as? DiskInfoApplication)?.currentPartitions
    }
}