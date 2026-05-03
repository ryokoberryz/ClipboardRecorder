package com.example.clipboardrecorder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clipboardrecorder.data.AppSettings
import com.example.clipboardrecorder.data.ClipboardRecord
import com.example.clipboardrecorder.data.ClipboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val records: StateFlow<List<ClipboardRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettings())

    private val _selectedRecords = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRecords: StateFlow<Set<Long>> = _selectedRecords.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredRecords = MutableStateFlow<List<ClipboardRecord>>(emptyList())
    val filteredRecords: StateFlow<List<ClipboardRecord>> = _filteredRecords.asStateFlow()

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun toggleSelection(recordId: Long) {
        val currentSelection = _selectedRecords.value.toMutableSet()
        if (currentSelection.contains(recordId)) {
            currentSelection.remove(recordId)
        } else {
            currentSelection.add(recordId)
        }
        _selectedRecords.value = currentSelection
    }

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedRecords.value = emptySet()
        }
    }

    fun selectAll() {
        _selectedRecords.value = records.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedRecords.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            repository.deleteRecordsByIds(_selectedRecords.value.toList())
            clearSelection()
            _isSelectionMode.value = false
        }
    }

    fun deleteRecord(record: ClipboardRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }

    fun copyRecord(record: ClipboardRecord) {
        val clip = ClipData.newPlainText("clipboard", record.content)
        clipboardManager.setPrimaryClip(clip)
    }

    fun copySelected() {
        viewModelScope.launch {
            val selectedIds = _selectedRecords.value.toList()
            val selectedRecordsList = repository.getRecordsByIds(selectedIds)
            val combinedText = selectedRecordsList.joinToString("\n") { it.content }
            
            val clip = ClipData.newPlainText("clipboard", combinedText)
            clipboardManager.setPrimaryClip(clip)
            
            clearSelection()
            _isSelectionMode.value = false
        }
    }

    suspend fun exportSelected(): String {
        val selectedIds = _selectedRecords.value.toList()
        val selectedRecordsList = repository.getRecordsByIds(selectedIds)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return selectedRecordsList.joinToString("\n\n") { record ->
            "${dateFormat.format(Date(record.timestamp))}\n${record.content}"
        }
    }

    fun exportAll(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return records.value.joinToString("\n\n") { record ->
            "${dateFormat.format(Date(record.timestamp))}\n${record.content}"
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _filteredRecords.value = emptyList()
        } else {
            _filteredRecords.value = records.value.filter { 
                it.content.contains(query, ignoreCase = true) 
            }
        }
    }

    fun updateMaxRecords(maxRecords: Int) {
        viewModelScope.launch {
            repository.updateMaxRecords(maxRecords)
        }
    }

    fun updateRetentionDays(days: Int) {
        viewModelScope.launch {
            repository.updateRetentionDays(days)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            repository.deleteAllRecords()
        }
    }
}
