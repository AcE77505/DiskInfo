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
    private val records: List<ImportExportRecord>
) : RecyclerView.Adapter<ImportExportRecordAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeText: TextView = view.findViewById(R.id.typeText)
        val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val messageText: TextView = view.findViewById(R.id.messageText)
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
    }

    override fun getItemCount() = records.size
}