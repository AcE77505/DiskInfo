package com.ace77505.diskinfo

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace77505.diskinfo.data.PartitionDataManager
import com.ace77505.diskinfo.data.PartitionInfo
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import com.google.android.material.color.DynamicColors
import com.ace77505.diskinfo.data.ImportExportManager
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: PartitionAdapter
    private lateinit var progressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator

    // 标题相关视图
    private lateinit var toolbarTitleNormal: TextView
    private lateinit var titleContainerImport: LinearLayout
    private lateinit var toolbarTitleImport: TextView
    private lateinit var toolbarSubtitle: TextView
    private lateinit var refreshBtn: ImageButton

    private val mainScope = MainScope()
    private var loadJob: Job? = null

    // 导入导出相关变量
    private var isViewingImportedData = false
    private var importedPartitions: List<PartitionInfo> = emptyList()

    // 设置 Activity Result Launcher
    private val settingsActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            applyTextSizeSettings()

            val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val currentHideLoopSetting = prefs.getBoolean("hide_loop_devices", false)

            if (this::adapter.isInitialized) {
                val shouldReloadData = adapter.hideLoopDevices != currentHideLoopSetting
                if (shouldReloadData) {
                    loadPartitionData()
                } else {
                    applyTextSizeSettings()
                }
            } else {
                applyTextSizeSettings()
            }
        }
    }

    // 导入导出 Activity Result Launcher
    private val importExportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val fileUri = data.getStringExtra(ImportExportActivity.EXTRA_IMPORT_FILE_URI)
                val fileName = data.getStringExtra(ImportExportActivity.EXTRA_IMPORT_FILE_NAME)

                if (fileUri != null) {
                    loadImportedPartitions(fileUri, fileName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)

        applyAppearanceMode()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        setupViews()
        setupRecyclerView()
        loadPartitionData()
    }

    /**
     * 应用外观模式设置
     */
    private fun applyAppearanceMode() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val appearanceMode = prefs.getInt("appearance_mode", 0)

        when (appearanceMode) {
            0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    private fun setupViews() {
        toolbarTitleNormal = findViewById(R.id.toolbar_title_normal)
        titleContainerImport = findViewById(R.id.title_container_import)
        toolbarTitleImport = findViewById(R.id.toolbar_title_import)
        toolbarSubtitle = findViewById(R.id.toolbar_subtitle)
        refreshBtn = findViewById(R.id.action_refresh)

        recyclerView = findViewById(R.id.partitionsRecyclerView)
        progressIndicator = findViewById(R.id.progressIndicator)

        // 设置按钮点击监听
        findViewById<ImageButton>(R.id.action_import_export).setOnClickListener {
            val intent = Intent(this, ImportExportActivity::class.java)
            importExportLauncher.launch(intent)
        }

        findViewById<ImageButton>(R.id.action_settings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsActivityResultLauncher.launch(intent)
        }

        // 刷新按钮点击和长按监听
        refreshBtn.setOnClickListener {
            if (isViewingImportedData) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "当前正在查看导入数据，无法刷新",
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                refreshPartitionData()
            }
        }

        refreshBtn.setOnLongClickListener {
            if (isViewingImportedData) {
                cancelImportView()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "已退出导入数据视图",
                    Snackbar.LENGTH_SHORT
                ).show()
                true
            } else {
                false
            }
        }

        // 标题长按监听
        toolbarTitleNormal.setOnLongClickListener {
            if (isViewingImportedData) {
                showCancelImportDialog()
                true
            } else {
                false
            }
        }

        // 导入状态标题长按监听
        toolbarTitleImport.setOnLongClickListener {
            if (isViewingImportedData) {
                showCancelImportDialog()
                true
            } else {
                false
            }
        }

        applyButtonTint()
    }

    /**
     * 设置按钮图标颜色
     */
    private fun applyButtonTint() {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorForeground, typedValue, true)
        val tint = if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }

        val importExportBtn = findViewById<ImageButton>(R.id.action_import_export)
        val refreshBtn = findViewById<ImageButton>(R.id.action_refresh)
        val settingsBtn = findViewById<ImageButton>(R.id.action_settings)

        importExportBtn.setColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
        refreshBtn.setColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
        settingsBtn.setColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
    }

    private fun setupRecyclerView() {
        adapter = PartitionAdapter(emptyList())
        adapter.initializeColors(this)

        adapter.setOnItemClickListener(object : PartitionAdapter.OnItemClickListener {
            override fun onItemClick(partition: PartitionInfo) {
                val intent = Intent(this@MainActivity, PartitionDetailActivity::class.java).apply {
                    putExtra(PartitionDetailActivity.EXTRA_PARTITION_INFO, partition.toJson())
                }
                startActivity(intent)
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        applyTextSizeSettings()
    }

    private fun loadPartitionData() {
        loadJob?.cancel()
        showLoading(true)

        loadJob = mainScope.launch {
            try {
                val partitions = withContext(Dispatchers.IO) {
                    if (isViewingImportedData) {
                        importedPartitions
                    } else {
                        val rawPartitions = PartitionDataManager.getPartitionInfo(applicationContext)
                        rawPartitions.map { partition ->
                            PartitionDataManager.processPartitionType(partition)
                        }
                    }
                }
                showLoading(false)
                updateUI(partitions)

                if (!isViewingImportedData) {
                    (application as? DiskInfoApplication)?.currentPartitions = partitions
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("获取分区信息失败: ${e.message}")
            }
        }
    }

    private fun updateUI(partitions: List<PartitionInfo>) {
        adapter.updateData(partitions)
    }

    // 应用字体大小设置
    private fun applyTextSizeSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        fun getScaleFactor(progress: Int): Float {
            return when (progress) {
                0 -> 0.7f
                1 -> 0.85f
                2 -> 1.0f
                3 -> 1.15f
                4 -> 1.3f
                else -> 1.0f
            }
        }

        val displayScale = getScaleFactor(prefs.getInt("display_size", 2))
        val partitionNameScale = getScaleFactor(prefs.getInt("partition_name_size", 2))
        val usageScale = getScaleFactor(prefs.getInt("usage_size", 2))
        val otherTextScale = getScaleFactor(prefs.getInt("other_text_size", 2))
        val hideLoopDevices = prefs.getBoolean("hide_loop_devices", false)

        val loopSettingChanged = this::adapter.isInitialized && adapter.hideLoopDevices != hideLoopDevices

        adapter.updateTextSizeSettings(displayScale, partitionNameScale, usageScale, otherTextScale)
        adapter.updateHideLoopDevicesSetting(hideLoopDevices)

        if (loopSettingChanged && !isViewingImportedData) {
            loadPartitionData()
        }
    }

    private fun showLoading(show: Boolean) {
        progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(error: String) {
        Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_LONG).show()
    }

    private fun showRefreshSuccessMessage() {
        Snackbar.make(findViewById(android.R.id.content), "分区数据已刷新", Snackbar.LENGTH_SHORT).show()
    }

    private fun showRefreshErrorMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), "刷新失败: $message", Snackbar.LENGTH_LONG).show()
    }

    private fun showImportSuccessMessage(fileName: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            "已导入分区数据: $fileName",
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * 加载导入的分区数据
     */
    private fun loadImportedPartitions(fileUri: String, fileName: String?) {
        loadJob?.cancel()
        showLoading(true)

        loadJob = mainScope.launch {
            try {
                val (partitions, extractedExportTime, record) = withContext(Dispatchers.IO) {
                    val uri = fileUri.toUri()
                    ImportExportManager.importPartitionsWithLongTime(this@MainActivity, uri)
                }

                if (partitions != null) {
                    showLoading(false)
                    showImportSuccessMessage(fileName ?: "文件")

                    // 将 Long 时间戳转换为格式化的字符串
                    val exportTimeString = if (extractedExportTime > 0L) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(extractedExportTime))
                    } else {
                        "未知时间"
                    }

                    displayImportedPartitions(partitions, exportTimeString)
                } else {
                    showLoading(false)
                    // 显示导入失败弹窗
                    showImportErrorDialog(record.message)
                }
            } catch (e: Exception) {
                showLoading(false)
                // 显示导入失败弹窗
                showImportErrorDialog(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 显示导入失败弹窗
     */
    private fun showImportErrorDialog(errorMessage: String) {
        val dialogMessage = "加载导入数据失败: $errorMessage"

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
    private fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
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
     * 显示导入的分区数据
     */
    private fun displayImportedPartitions(partitions: List<PartitionInfo>, exportTime: String?) {
        isViewingImportedData = true
        importedPartitions = partitions

        setImportSubtitle(exportTime)
        updateUI(partitions)
        updateRefreshButtonState()
        (application as? DiskInfoApplication)?.currentPartitions = partitions
    }

    /**
     * 设置导入状态的副标题
     */
    private fun setImportSubtitle(exportTime: String?) {
        toolbarTitleNormal.visibility = View.GONE
        titleContainerImport.visibility = View.VISIBLE

        toolbarTitleImport.text = getString(R.string.app_name)
        toolbarSubtitle.text = exportTime ?: "导入数据"
        toolbarSubtitle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    /**
     * 清除副标题
     */
    private fun clearSubtitle() {
        titleContainerImport.visibility = View.GONE
        toolbarTitleNormal.visibility = View.VISIBLE
    }

    /**
     * 更新刷新按钮状态
     */
    private fun updateRefreshButtonState() {
        refreshBtn.contentDescription = if (isViewingImportedData) "长按退出导入" else "刷新"
    }

    /**
     * 显示取消导入对话框
     */
    private fun showCancelImportDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("取消查看导入数据")
            .setMessage("确定要返回查看本机分区信息吗？")
            .setPositiveButton("确定") { _, _ -> cancelImportView() }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 取消导入视图，返回本机数据
     */
    private fun cancelImportView() {
        isViewingImportedData = false
        importedPartitions = emptyList()
        clearSubtitle()
        updateRefreshButtonState()
        loadPartitionData()
    }

    /**
     * 刷新分区数据
     */
    private fun refreshPartitionData() {
        loadJob?.cancel()
        showLoading(true)

        loadJob = mainScope.launch {
            try {
                val partitions = withContext(Dispatchers.IO) {
                    val rawPartitions = PartitionDataManager.getPartitionInfo(applicationContext)
                    rawPartitions.map { partition ->
                        PartitionDataManager.processPartitionType(partition)
                    }
                }

                showLoading(false)
                updateUI(partitions)
                showRefreshSuccessMessage()
                (application as? DiskInfoApplication)?.currentPartitions = partitions
            } catch (e: Exception) {
                showLoading(false)
                showRefreshErrorMessage(e.message ?: "刷新失败")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (isViewingImportedData) {
            updateRefreshButtonState()
        } else {
            clearSubtitle()
        }

        applyButtonTint()
    }
}