package com.ace77505.diskinfo

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ace77505.diskinfo.data.PartitionInfo
import com.google.android.material.appbar.MaterialToolbar

class PartitionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTITION_INFO = "extra_partition_info"
    }

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: PartitionDetailAdapter
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_partition_detail)

        // 设置状态栏颜色
        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_background)

        setupViews()
        setupRecyclerView()
        setupBackPressedHandler()
        loadPartitionData()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 显示返回箭头
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        recyclerView = findViewById(R.id.detailRecyclerView)
    }

    private fun setupRecyclerView() {
        adapter = PartitionDetailAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupBackPressedHandler() {
        // 使用新的 OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun loadPartitionData() {
        // 使用新的 getParcelableExtra 方法
        val partition = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PARTITION_INFO, PartitionInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PARTITION_INFO)
        }

        if (partition != null) {
            updateUI(partition)
        } else {
            finish() // 如果没有分区数据，关闭页面
        }
    }

    private fun updateUI(partition: PartitionInfo) {
        // 标题栏只显示分区名
        toolbar.title = partition.name
        supportActionBar?.title = partition.name

        // 准备详情数据
        adapter.updateData(partition)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // 使用新的返回处理方式
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}