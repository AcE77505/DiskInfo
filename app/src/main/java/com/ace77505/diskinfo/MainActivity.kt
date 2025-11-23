package com.ace77505.diskinfo

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.Build
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace77505.diskinfo.data.PartitionDataManager
import com.ace77505.diskinfo.data.PartitionInfo
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import androidx.core.view.get
import com.google.android.material.color.DynamicColors
import androidx.core.view.size

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: PartitionAdapter
    private lateinit var progressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var toolbar: MaterialToolbar

    private val mainScope = MainScope()
    private var loadJob: Job? = null

    // 使用新的 Activity Result API 替代 startActivityForResult
    private val settingsActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 只在设置被修改时才更新界面
            applyTextSizeSettings()

            // 修复：当隐藏loop设备设置改变时，重新加载分区数据
            val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val currentHideLoopSetting = prefs.getBoolean("hide_loop_devices", false)

            // 如果隐藏loop设备的设置发生了变化，重新加载数据
            if (this::adapter.isInitialized) {
                val shouldReloadData = adapter.hideLoopDevices != currentHideLoopSetting
                if (shouldReloadData) {
                    loadPartitionData() // 重新加载完整的分区数据
                } else {
                    applyTextSizeSettings() // 只更新字体设置
                }
            } else {
                applyTextSizeSettings()
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
        // 直接使用硬编码的默认值0
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
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.partitionsRecyclerView)
        progressIndicator = findViewById(R.id.progressIndicator)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        // 解析 ?attr/colorOnSurface 为色值
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorForeground, typedValue, true)
        val tint = if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }

        // 给所有菜单图标染色（包括溢出图标）
        for (i in 0 until menu.size) {
            menu[i].icon?.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_ATOP)
        }
        return true
    }

    private fun setupRecyclerView() {
        adapter = PartitionAdapter(emptyList())
        // 关键：在设置适配器后立即初始化颜色资源
        adapter.initializeColors(this)

        // 添加点击监听器
        adapter.setOnItemClickListener(object : PartitionAdapter.OnItemClickListener {
            override fun onItemClick(partition: PartitionInfo) {
                val intent = Intent(this@MainActivity, PartitionDetailActivity::class.java).apply {
                    // 使用 JSON 字符串代替 Parcelable
                    putExtra(PartitionDetailActivity.EXTRA_PARTITION_INFO, partition.toJson())
                }
                startActivity(intent)
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 初始加载设置
        applyTextSizeSettings()
    }

    private fun loadPartitionData() {
        loadJob?.cancel()
        showLoading(true)

        loadJob = mainScope.launch {
            try {
                val partitions = withContext(Dispatchers.IO) {
                    // 获取原始分区数据
                    val rawPartitions = PartitionDataManager.getPartitionInfo(applicationContext)

                    // 关键优化：预处理分区类型（复用现有判断逻辑）
                    rawPartitions.map { partition ->
                        PartitionDataManager.processPartitionType(partition)
                    }
                }
                showLoading(false)
                updateUI(partitions)
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

        // 将 SeekBar 的进度值 (0-4) 转换为缩放比例
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

        // 检查隐藏loop设备设置是否改变
        val loopSettingChanged = this::adapter.isInitialized && adapter.hideLoopDevices != hideLoopDevices

        adapter.updateTextSizeSettings(displayScale, partitionNameScale, usageScale, otherTextScale)
        adapter.updateHideLoopDevicesSetting(hideLoopDevices)

        // 如果隐藏loop设备设置改变，重新加载数据
        if (loopSettingChanged) {
            loadPartitionData()
        }
    }

    private fun showLoading(show: Boolean) {
        progressIndicator.visibility = if (show) com.google.android.material.progressindicator.LinearProgressIndicator.VISIBLE else com.google.android.material.progressindicator.LinearProgressIndicator.GONE
    }

    private fun showError(error: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            error,
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * 显示刷新成功消息
     */
    private fun showRefreshSuccessMessage() {
        Snackbar.make(findViewById(android.R.id.content), "分区数据已刷新", Snackbar.LENGTH_SHORT).show()
    }

    /**
     * 显示刷新错误消息
     */
    private fun showRefreshErrorMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), "刷新失败: $message", Snackbar.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                // 执行刷新操作
                refreshPartitionData()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                // 使用新的 Activity Result API 替代 startActivityForResult
                settingsActivityResultLauncher.launch(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                    // 重新获取分区数据
                    val rawPartitions = PartitionDataManager.getPartitionInfo(applicationContext)

                    // 预处理分区类型
                    rawPartitions.map { partition ->
                        PartitionDataManager.processPartitionType(partition)
                    }
                }

                showLoading(false)
                updateUI(partitions)
                showRefreshSuccessMessage()
            } catch (e: Exception) {
                showLoading(false)
                showRefreshErrorMessage(e.message ?: "刷新失败")
            }
        }
    }

    // 移除已弃用的 onActivityResult 方法
    // override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    //     super.onActivityResult(requestCode, resultCode, data)
    //     if (requestCode == 1 && resultCode == RESULT_OK) {
    //         // 这部分逻辑已移到 settingsActivityResultLauncher 的回调中
    //     }
    // }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面时检查主题变化
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}