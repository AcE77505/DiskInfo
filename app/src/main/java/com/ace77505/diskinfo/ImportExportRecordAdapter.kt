package com.ace77505.diskinfo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ace77505.diskinfo.data.ImportExportRecord
import java.text.SimpleDateFormat
import java.util.Locale

class ImportExportRecordAdapter(
    private var records: List<ImportExportRecord>
) : RecyclerView.Adapter<ImportExportRecordAdapter.ViewHolder>() {

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()
    private var onSelectionModeChangeListener: ((Boolean) -> Unit)? = null
    private var onItemLongClickListener: ((Int) -> Unit)? = null
    private var onSelectionChangeListener: ((Int) -> Unit)? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeText: TextView = view.findViewById(R.id.typeText)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val selectionOverlay: View = view.findViewById(R.id.selectionOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_import_export_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]

        holder.typeText.text = when (record.type) {
            ImportExportRecord.TYPE_IMPORT -> "导入"
            ImportExportRecord.TYPE_EXPORT -> "导出"
            else -> record.type
        }

        holder.fileNameText.text = record.fileName

        val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        holder.timeText.text = timeFormat.format(record.timestamp)

        holder.messageText.text = record.message

        // 设置颜色
        val context = holder.itemView.context
        val color = if (record.success) {
            ContextCompat.getColor(context, android.R.color.holo_green_dark)
        } else {
            ContextCompat.getColor(context, android.R.color.holo_red_dark)
        }
        holder.messageText.setTextColor(color)

        // 选择模式相关
        val isSelected = selectedItems.contains(position)
        holder.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.itemView.isActivated = isSelected

        // 点击事件
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            }
            // 正常模式下点击不做任何事
        }

        // 长按事件
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                onItemLongClickListener?.invoke(position)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount() = records.size

    fun setOnSelectionModeChangeListener(listener: (Boolean) -> Unit) {
        this.onSelectionModeChangeListener = listener
    }

    fun setOnItemLongClickListener(listener: (Int) -> Unit) {
        this.onItemLongClickListener = listener
    }

    fun setOnSelectionChangeListener(listener: (Int) -> Unit) {
        this.onSelectionChangeListener = listener
    }

    fun updateData(newRecords: List<ImportExportRecord>) {
        this.records = newRecords
        notifyDataSetChanged()
    }

    fun enterSelectionMode(position: Int) {
        isSelectionMode = true
        selectedItems.add(position)
        notifyDataSetChanged()
        onSelectionModeChangeListener?.invoke(true)
        onSelectionChangeListener?.invoke(selectedItems.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionModeChangeListener?.invoke(false)
        onSelectionChangeListener?.invoke(0)
    }

    fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position)
        } else {
            selectedItems.add(position)
        }
        notifyItemChanged(position)

        // 通知选择数量变化
        onSelectionChangeListener?.invoke(selectedItems.size)

        // 如果没有选中的项目，自动退出选择模式
        if (selectedItems.isEmpty()) {
            exitSelectionMode()
        }
    }

    fun getSelectedItems(): Set<Int> = selectedItems

    fun getSelectedRecords(): List<ImportExportRecord> {
        return selectedItems.map { records[it] }
    }

}