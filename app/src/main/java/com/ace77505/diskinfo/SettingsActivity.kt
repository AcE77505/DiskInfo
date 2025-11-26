package com.ace77505.diskinfo

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var displaySizeSeekBar: SeekBar
    private lateinit var partitionNameSizeSeekBar: SeekBar
    private lateinit var usageSizeSeekBar: SeekBar
    private lateinit var otherTextSizeSeekBar: SeekBar
    private lateinit var hideLoopDevicesSwitch: SwitchMaterial
    private lateinit var defaultCopyInfoSwitch: SwitchMaterial
    private lateinit var appearanceSpinner: Spinner

    private lateinit var displaySizeValue: TextView
    private lateinit var partitionNameSizeValue: TextView
    private lateinit var usageSizeValue: TextView
    private lateinit var otherTextSizeValue: TextView

    private lateinit var saveImportFilesSwitch: SwitchMaterial
    private var settingsChanged = false
    private var appearanceOptions = arrayOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_settings)

        // 初始化外观选项（根据系统版本）
        initAppearanceOptions()

        setupBackPressedHandler()
        setupViews()
        loadSettings()
        setupListeners()
        updateAllValueDisplays()
    }

    /**
     * 初始化外观选项
     */
    private fun initAppearanceOptions() {
        appearanceOptions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // 9.0 及以上支持系统级深色模式
            resources.getStringArray(R.array.appearance_options)
        } else {
            // 8.1 及以下不支持系统级深色模式
            resources.getStringArray(R.array.appearance_options_legacy)
        }
    }

    private fun setupBackPressedHandler() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(if (settingsChanged) RESULT_OK else RESULT_CANCELED)
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun setupViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // 导航图标染色
        val onSurfaceColor = resolveColorOnSurface()
        toolbar.navigationIcon?.colorFilter =
            PorterDuffColorFilter(onSurfaceColor, PorterDuff.Mode.SRC_ATOP)

        toolbar.setOnMenuItemClickListener { item ->
            item.icon?.colorFilter =
                PorterDuffColorFilter(onSurfaceColor, PorterDuff.Mode.SRC_ATOP)
            false
        }

        displaySizeSeekBar = findViewById(R.id.displaySizeSeekBar)
        partitionNameSizeSeekBar = findViewById(R.id.partitionNameSizeSeekBar)
        usageSizeSeekBar = findViewById(R.id.usageSizeSeekBar)
        otherTextSizeSeekBar = findViewById(R.id.otherTextSizeSeekBar)
        hideLoopDevicesSwitch = findViewById(R.id.hideLoopDevicesSwitch)
        defaultCopyInfoSwitch = findViewById(R.id.defaultCopyInfoSwitch)
        appearanceSpinner = findViewById(R.id.appearanceSpinner)
        saveImportFilesSwitch = findViewById(R.id.saveImportFilesSwitch)

        displaySizeValue = findViewById(R.id.displaySizeValue)
        partitionNameSizeValue = findViewById(R.id.partitionNameSizeValue)
        usageSizeValue = findViewById(R.id.usageSizeValue)
        otherTextSizeValue = findViewById(R.id.otherTextSizeValue)

        // 设置外观下拉框适配器
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, appearanceOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        appearanceSpinner.adapter = adapter
    }

    private fun resolveColorOnSurface(): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorForeground, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        displaySizeSeekBar.progress = prefs.getInt("display_size", 2)
        partitionNameSizeSeekBar.progress = prefs.getInt("partition_name_size", 2)
        usageSizeSeekBar.progress = prefs.getInt("usage_size", 2)
        otherTextSizeSeekBar.progress = prefs.getInt("other_text_size", 2)
        hideLoopDevicesSwitch.isChecked = prefs.getBoolean("hide_loop_devices", false)
        defaultCopyInfoSwitch.isChecked = prefs.getBoolean("default_copy_info", false)
        saveImportFilesSwitch.isChecked = prefs.getBoolean("save_import_files", true)

        // 0的意思是默认值
        val appearanceMode = prefs.getInt("appearance_mode", 0)
        appearanceSpinner.setSelection(appearanceMode)
    }

    private fun setupListeners() {
        saveImportFilesSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("save_import_files", isChecked)
            settingsChanged = true
        }
        displaySizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    saveSetting("display_size", progress)
                    updateValueDisplay(displaySizeValue, progress)
                    settingsChanged = true
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        partitionNameSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    saveSetting("partition_name_size", progress)
                    updateValueDisplay(partitionNameSizeValue, progress)
                    settingsChanged = true
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        usageSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    saveSetting("usage_size", progress)
                    updateValueDisplay(usageSizeValue, progress)
                    settingsChanged = true
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        otherTextSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    saveSetting("other_text_size", progress)
                    updateValueDisplay(otherTextSizeValue, progress)
                    settingsChanged = true
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        hideLoopDevicesSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("hide_loop_devices", isChecked)
            settingsChanged = true
        }

        defaultCopyInfoSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("default_copy_info", isChecked)
            settingsChanged = true
        }

        // 外观下拉框监听器
        appearanceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                saveSetting("appearance_mode", position)
                // 直接应用外观模式
                applyAppearanceMode(position)
                settingsChanged = true
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    /**
     * 应用外观模式
     */
    private fun applyAppearanceMode(mode: Int) {
        when (mode) {
            0 -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    // 跟随系统
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    // 浅色模式
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
            1 -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    // 浅色模式
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                } else {
                    // 深色模式
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            2 -> {
                // 深色模式
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    private fun saveSetting(key: String, value: Int) {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit { putInt(key, value) }
    }

    private fun saveSetting(key: String, value: Boolean) {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit { putBoolean(key, value) }
    }

    private fun updateValueDisplay(textView: TextView, progress: Int) {
        val displayNames = resources.getStringArray(R.array.text_size_display_names)
        textView.text = displayNames.getOrElse(progress) { displayNames[2] }
    }

    private fun updateAllValueDisplays() {
        updateValueDisplay(displaySizeValue, displaySizeSeekBar.progress)
        updateValueDisplay(partitionNameSizeValue, partitionNameSizeSeekBar.progress)
        updateValueDisplay(usageSizeValue, usageSizeSeekBar.progress)
        updateValueDisplay(otherTextSizeValue, otherTextSizeSeekBar.progress)
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(if (settingsChanged) RESULT_OK else RESULT_CANCELED)
        finish()
        return true
    }
}