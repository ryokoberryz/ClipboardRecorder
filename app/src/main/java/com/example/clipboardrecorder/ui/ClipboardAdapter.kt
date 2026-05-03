package com.example.clipboardrecorder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.clipboardrecorder.R
import com.example.clipboardrecorder.data.ClipboardRecord
import com.example.clipboardrecorder.databinding.ItemClipboardRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardAdapter(
    private val onItemClick: (ClipboardRecord) -> Unit,
    private val onItemLongClick: (ClipboardRecord) -> Boolean,
    private val onDeleteClick: (ClipboardRecord) -> Unit,
    private val onSelectionChange: (Long, Boolean) -> Unit
) : ListAdapter<ClipboardRecord, ClipboardAdapter.ClipboardViewHolder>(ClipboardDiffCallback()) {

    private var isSelectionMode = false
    private var selectedItems = emptySet<Long>()

    fun setSelectionMode(mode: Boolean) {
        isSelectionMode = mode
        if (!mode) {
            selectedItems = emptySet()
        }
        notifyDataSetChanged()
    }

    fun setSelectedItems(items: Set<Long>) {
        selectedItems = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val binding = ItemClipboardRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClipboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClipboardViewHolder(
        private val binding: ItemClipboardRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: ClipboardRecord) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            binding.tvTimestamp.text = dateFormat.format(Date(record.timestamp))
            binding.tvContent.text = record.content
            
            val isSelected = selectedItems.contains(record.id)
            binding.checkbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected
            
            binding.btnDelete.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
            
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    onSelectionChange(record.id, !isSelected)
                } else {
                    onItemClick(record)
                }
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(record)
            }

            binding.checkbox.setOnClickListener {
                onSelectionChange(record.id, !isSelected)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(record)
            }

            val content = record.content
            binding.tvContent.text = if (content.length > 200) {
                content.substring(0, 200) + "..."
            } else {
                content
            }
        }
    }

    class ClipboardDiffCallback : DiffUtil.ItemCallback<ClipboardRecord>() {
        override fun areItemsTheSame(oldItem: ClipboardRecord, newItem: ClipboardRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClipboardRecord, newItem: ClipboardRecord): Boolean {
            return oldItem == newItem
        }
    }
}
