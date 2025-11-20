package com.ace77505.diskinfo

import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace77505.diskinfo.data.PartitionDataManager
import com.ace77505.diskinfo.data.PartitionInfo
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.*
import androidx.core.view.size
import androidx.core.view.get

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: PartitionAdapter
    private lateinit var progressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var toolbar: MaterialToolbar

    private val mainScope = MainScope()
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 强制设置状态栏颜色
        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_background)

        // 动态设置状态栏图标颜色（仅在 API 23+ 可用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isDarkTheme(this)) {
                // 深色主题 - 状态栏图标为白色
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                // 浅色主题 - 状态栏图标为黑色
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        // 动态设置导航栏图标颜色（仅在 API 27+ 可用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            if (isDarkTheme(this)) {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        setupViews()
        setupRecyclerView()
        loadPartitionData()
    }

    // 检查当前是否为深色主题
    private fun isDarkTheme(context: Context): Boolean {
        val flag = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return flag == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black))

        recyclerView = findViewById(R.id.partitionsRecyclerView)
        progressIndicator = findViewById(R.id.progressIndicator)
    }
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        for (i in 0 until menu.size) {
            val menuItem = menu[i]
            val icon = menuItem.icon
            if (icon != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 使用主题颜色而不是硬编码的黑色
                    icon.colorFilter = BlendModeColorFilter(
                        ContextCompat.getColor(this, R.color.text_primary),
                        BlendMode.SRC_ATOP
                    )
                } else {
                    @Suppress("DEPRECATION")
                    icon.setColorFilter(
                        ContextCompat.getColor(this, R.color.text_primary),
                        PorterDuff.Mode.SRC_ATOP
                    )
                }
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }
    private fun setupRecyclerView() {
        adapter = PartitionAdapter(emptyList())
        // 关键：在设置适配器后立即初始化颜色资源
        adapter.initializeColors(this)

        // 添加点击监听器
        adapter.setOnItemClickListener(object : PartitionAdapter.OnItemClickListener {
            override fun onItemClick(partition: PartitionInfo) {
                val intent = Intent(this@MainActivity, PartitionDetailActivity::class.java).apply {
                    putExtra(PartitionDetailActivity.EXTRA_PARTITION_INFO, partition)
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
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            error,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(intent, 1) // 使用 startActivityForResult
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 处理设置页面返回的结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
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

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}