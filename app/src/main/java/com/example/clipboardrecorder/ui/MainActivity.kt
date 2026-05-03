package com.example.clipboardrecorder.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.clipboardrecorder.BuildConfig
import com.example.clipboardrecorder.R
import com.example.clipboardrecorder.data.ClipboardRecord
import com.example.clipboardrecorder.databinding.ActivityMainBinding
import com.example.clipboardrecorder.databinding.DialogLogsBinding
import com.example.clipboardrecorder.databinding.DialogSettingsBinding
import com.example.clipboardrecorder.service.ClipboardListenerService
import com.example.clipboardrecorder.service.FloatingWindowService
import com.example.clipboardrecorder.utils.AppLogger
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ClipboardAdapter
    
    private var isServiceBound = false
    private var isFloatingWindowEnabled = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBatteryOptimization()
        } else {
            showPermissionDeniedMessage()
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Settings.canDrawOverlays(this)) {
            enableFloatingWindow()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能使用此功能", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeData()
        checkPermissionsAndStartService()
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        super.onStop()
        unbindService()
    }

    private fun bindService() {
        val intent = Intent(this, ClipboardListenerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun setupRecyclerView() {
        adapter = ClipboardAdapter(
            onItemClick = { record ->
                if (viewModel.isSelectionMode.value) {
                    viewModel.toggleSelection(record.id)
                } else {
                    viewModel.copyRecord(record)
                    showCopySuccessMessage()
                }
            },
            onItemLongClick = { record ->
                if (!viewModel.isSelectionMode.value) {
                    viewModel.toggleSelectionMode()
                    viewModel.toggleSelection(record.id)
                }
                true
            },
            onDeleteClick = { record ->
                showDeleteConfirmDialog(record)
            },
            onSelectionChange = { recordId, _ ->
                viewModel.toggleSelection(recordId)
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupListeners() {
        binding.fabSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnSelectionMode.setOnClickListener {
            viewModel.toggleSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            viewModel.selectAll()
        }

        binding.btnDeleteSelected.setOnClickListener {
            showDeleteSelectedConfirmDialog()
        }

        binding.btnCopySelected.setOnClickListener {
            viewModel.copySelected()
            showCopySuccessMessage()
        }

        binding.btnExportSelected.setOnClickListener {
            exportSelectedRecords()
        }

        binding.btnExportAll.setOnClickListener {
            exportAllRecords()
        }

        binding.btnClearSelection.setOnClickListener {
            viewModel.clearSelection()
        }

        binding.btnLogs.setOnClickListener {
            showLogsDialog()
        }

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText ?: "")
                return true
            }
        })
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.records.collect { records ->
                val query = viewModel.searchQuery.value
                if (query.isEmpty()) {
                    adapter.submitList(records)
                }
                updateEmptyView(records.isEmpty())
            }
        }

        lifecycleScope.launch {
            viewModel.filteredRecords.collect { records ->
                if (viewModel.searchQuery.value.isNotEmpty()) {
                    adapter.submitList(records)
                    updateEmptyView(records.isEmpty())
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isSelectionMode.collect { isSelectionMode ->
                updateSelectionModeUI(isSelectionMode)
            }
        }

        lifecycleScope.launch {
            viewModel.selectedRecords.collect { selectedIds ->
                adapter.setSelectedItems(selectedIds)
                updateSelectionCount(selectedIds.size)
            }
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerView.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateSelectionModeUI(isSelectionMode: Boolean) {
        binding.selectionModeBar.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        binding.normalModeBar.visibility = if (isSelectionMode) android.view.View.GONE else android.view.View.VISIBLE
        adapter.setSelectionMode(isSelectionMode)
    }

    private fun updateSelectionCount(count: Int) {
        binding.tvSelectionCount.text = getString(R.string.selected_count, count)
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                checkBatteryOptimization()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        } else {
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
            
            if (!isIgnoring) {
                showBatteryOptimizationDialog()
            } else {
                startClipboardService()
                showFloatingWindowDialog()
            }
        } else {
            startClipboardService()
            showFloatingWindowDialog()
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要关闭电池优化")
            .setMessage("为了确保剪贴板监听服务在后台持续运行，请将本应用加入电池优化白名单。")
            .setPositiveButton("去设置") { _, _ ->
                requestIgnoreBatteryOptimizations()
            }
            .setNegativeButton("稍后") { _, _ ->
                startClipboardService()
                showFloatingWindowDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
        startClipboardService()
        showFloatingWindowDialog()
    }

    private fun showFloatingWindowDialog() {
        AlertDialog.Builder(this)
            .setTitle("启用悬浮窗按钮")
            .setMessage("点击悬浮窗按钮可以快速读取当前剪贴板内容并保存。\n\n悬浮窗可以拖动，点击读取剪贴板，长按关闭。")
            .setPositiveButton("启用") { _, _ ->
                checkOverlayPermission()
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            enableFloatingWindow()
        }
    }

    private fun enableFloatingWindow() {
        FloatingWindowService.show(this)
        isFloatingWindowEnabled = true
        Toast.makeText(this, "悬浮窗已启用，点击读取剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun disableFloatingWindow() {
        FloatingWindowService.hide(this)
        isFloatingWindowEnabled = false
    }

    private fun startClipboardService() {
        ClipboardListenerService.start(this)
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionDeniedMessage() {
        Snackbar.make(
            binding.root,
            R.string.permission_denied_message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.settings) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        }.show()
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        
        lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                dialogBinding.etMaxRecords.setText(settings.maxRecords.toString())
                dialogBinding.etRetentionDays.setText(settings.retentionDays.toString())
                dialogBinding.switchShowToast.isChecked = settings.showToast
                dialogBinding.switchAutoRecord.isChecked = settings.autoRecordEnabled
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings) + " - " + getString(R.string.version, BuildConfig.VERSION_NAME))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val maxRecords = dialogBinding.etMaxRecords.text.toString().toIntOrNull() ?: 100
                val retentionDays = dialogBinding.etRetentionDays.text.toString().toIntOrNull() ?: 30
                val showToast = dialogBinding.switchShowToast.isChecked
                val autoRecord = dialogBinding.switchAutoRecord.isChecked

                viewModel.updateMaxRecords(maxRecords)
                viewModel.updateRetentionDays(retentionDays)
                viewModel.updateShowToast(showToast)
                viewModel.updateAutoRecord(autoRecord)

                Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton("悬浮窗", null)
            .create()
        
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (isFloatingWindowEnabled) {
                    disableFloatingWindow()
                    Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    checkOverlayPermission()
                }
            }
        }
        
        dialog.show()
    }

    private fun showLogsDialog() {
        val dialogBinding = DialogLogsBinding.inflate(layoutInflater)
        
        dialogBinding.tvLogs.text = AppLogger.getLogs()
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.app_logs)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        dialogBinding.btnClearLogs.setOnClickListener {
            AppLogger.clearLogs()
            dialogBinding.tvLogs.text = ""
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
        }
        
        dialogBinding.btnCopyLogs.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("logs", AppLogger.getLogs())
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
    }

    private fun showDeleteConfirmDialog(record: ClipboardRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_record)
            .setMessage(R.string.delete_record_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteRecord(record)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteSelectedConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_selected)
            .setMessage(getString(R.string.delete_selected_message, viewModel.selectedRecords.value.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteSelected()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportSelectedRecords() {
        lifecycleScope.launch {
            val selectedIds = viewModel.selectedRecords.value.toList()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.no_records_selected, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val records = viewModel.records.value.filter { it.id in selectedIds }
            val content = formatRecordsForExport(records)
            saveToFile(content, "selected_records")
        }
    }

    private fun exportAllRecords() {
        lifecycleScope.launch {
            val records = viewModel.records.value
            if (records.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.no_records, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val content = formatRecordsForExport(records)
            saveToFile(content, "all_records")
        }
    }

    private fun formatRecordsForExport(records: List<ClipboardRecord>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return records.joinToString("\n\n") { record ->
            "${dateFormat.format(Date(record.timestamp))}\n${record.content}"
        }
    }

    private fun saveToFile(content: String, fileName: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "${fileName}_${timestamp}.txt")
            
            FileWriter(file).use { writer ->
                writer.write(content)
            }

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_records)))
            
            Toast.makeText(this, getString(R.string.export_success, file.name), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showCopySuccessMessage() {
        Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show()
    }
}
