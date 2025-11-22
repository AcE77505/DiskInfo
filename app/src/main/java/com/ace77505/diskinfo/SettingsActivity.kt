package com.ace77505.diskinfo

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var displaySizeSeekBar: SeekBar
    private lateinit var partitionNameSizeSeekBar: SeekBar
    private lateinit var usageSizeSeekBar: SeekBar
    private lateinit var otherTextSizeSeekBar: SeekBar
    private lateinit var hideLoopDevicesSwitch: SwitchMaterial

    private lateinit var displaySizeValue: TextView
    private lateinit var partitionNameSizeValue: TextView
    private lateinit var usageSizeValue: TextView
    private lateinit var otherTextSizeValue: TextView

    private var settingsChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupViews()
        loadSettings()
        setupListeners()
        updateAllValueDisplays()
    }

    private fun setupViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // 导航图标染色：跟随主题 colorOnSurface
        val onSurfaceColor = resolveColorOnSurface()
        toolbar.navigationIcon?.colorFilter =
            PorterDuffColorFilter(onSurfaceColor, PorterDuff.Mode.SRC_ATOP)

        // 菜单图标染色（可选，若仍想手动）
        // 若决定完全交给 MD3，可删除下方循环
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

        displaySizeValue = findViewById(R.id.displaySizeValue)
        partitionNameSizeValue = findViewById(R.id.partitionNameSizeValue)
        usageSizeValue = findViewById(R.id.usageSizeValue)
        otherTextSizeValue = findViewById(R.id.otherTextSizeValue)
    }

    /**
     * 通过主题属性解析 colorOnSurface 色值
     */
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
    }

    private fun setupListeners() {
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
    }

    private fun saveSetting(key: String, value: Int) {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit { putInt(key, value) }
    }

    private fun saveSetting(key: String, value: Boolean) {
        getSharedPreferences("app_settings", MODE_PRIVATE).edit { putBoolean(key, value) }
    }

    private fun updateValueDisplay(textView: TextView, progress: Int) {
        val displayText = when (progress) {
            0 -> "很小"
            1 -> "较小"
            2 -> "标准"
            3 -> "较大"
            4 -> "很大"
            else -> "标准"
        }
        textView.text = displayText
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


    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        setResult(if (settingsChanged) RESULT_OK else RESULT_CANCELED)
        super.onBackPressed()
    }
}