package com.ace77505.diskinfo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace77505.diskinfo.data.ImportExportManager
import com.ace77505.diskinfo.data.ImportExportRecord
import com.ace77505.diskinfo.data.PartitionInfo
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ImportExportActivity : AppCompatActivity() {

    private lateinit var adapter: ImportExportRecordAdapter
    private val records = mutableListOf<ImportExportRecord>()

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
        uri?.let {
            CoroutineScope(Dispatchers.Main).launch {
                val (partitions, exportTime, record) = ImportExportManager.importPartitions(
                    this@ImportExportActivity,
                    it
                )

                addRecord(record)

                if (record.success && partitions != null) {
                    showMessage("导入成功: ${record.fileName}")
                    setResult(RESULT_OK, Intent().apply {
                        putExtra(EXTRA_IMPORT_FILE_URI, uri.toString())
                        putExtra(EXTRA_IMPORT_FILE_NAME, record.fileName)
                        putExtra(EXTRA_EXPORT_TIME, exportTime) // 传递原始值
                    })
                    finish()
                } else {
                    showMessage("导入失败: ${record.message}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_export)

        setupToolbar()
        setupRecyclerView()
        loadHistory()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "导入导出记录"
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recordsRecyclerView)
        adapter = ImportExportRecordAdapter(records)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        // 这里可以从 SharedPreferences 或数据库加载历史记录
        // 暂时使用空列表
    }

    private fun addRecord(record: ImportExportRecord) {
        records.add(0, record) // 新的记录添加到顶部
        adapter.notifyItemInserted(0)
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recordsRecyclerView)
        recyclerView.scrollToPosition(0)

        // 保存到历史记录（可选）
        saveRecordToHistory()
    }

    private fun saveRecordToHistory() {
        // 这里可以保存到 SharedPreferences 或数据库
        // 暂时不实现具体保存逻辑
    }

    private fun getCurrentPartitionsFromMainActivity(): List<PartitionInfo>? {
        // 通过 Application 类获取当前分区数据
        return (application as? DiskInfoApplication)?.currentPartitions
    }

    private fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.import_export_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export -> {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
                val fileName = "partition_info_$timestamp.json"
                exportLauncher.launch(fileName)
                true
            }
            R.id.action_import -> {
                importLauncher.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_IMPORT_FILE_URI = "import_file_uri"
        const val EXTRA_IMPORT_FILE_NAME = "import_file_name"
        const val EXTRA_EXPORT_TIME = "export_time"
    }
}