package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.ui.manager.ExportManager
import com.example.meterkenshin.ui.manager.FileGroup
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.ui.manager.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private var currentUsername: String? = null

    private var exportManager: ExportManager? = null

    private val context = application

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _fileGroups = MutableStateFlow<List<FileGroup>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredGroups = MutableStateFlow<List<FileGroup>>(emptyList())
    val filteredGroups: StateFlow<List<FileGroup>> = _filteredGroups.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterFiles()
    }

    private fun filterFiles() {
        val query = _searchQuery.value.lowercase()
        _filteredGroups.value = if (query.isEmpty()) {
            _fileGroups.value
        } else {
            _fileGroups.value.map { group ->
                FileGroup(
                    name = group.name,
                    files = group.files.filter { it.name.lowercase().contains(query) }
                )
            }.filter { it.files.isNotEmpty() }
        }
    }

    fun toggleFileSelection(fileName: String, isSelected: Boolean) {
        val current = _selectedFiles.value.toMutableSet()
        if (isSelected) {
            current.add(fileName)
        } else {
            current.remove(fileName)
        }
        _selectedFiles.value = current
    }

    fun selectAll() {
        _selectedFiles.value = _files.value.map { it.name }.toSet()
    }

    fun selectNone() {
        _selectedFiles.value = emptySet()
    }

    // ✅ ADD: Method to set current user
    fun setCurrentUser(username: String) {
        currentUsername = username
        exportManager = ExportManager(context, username)
        Log.d("ExportViewModel", "Export manager initialized for user: $username")
    }

    // ✅ UPDATE: All methods that use exportManager - add null check
    fun loadFiles() {
        viewModelScope.launch {
            val manager = exportManager ?: return@launch
            _fileGroups.value = manager.getGroupedFiles()
            _files.value = manager.getAvailableFiles()
            filterFiles()
        }
    }

    fun exportSelectedFiles() {
        viewModelScope.launch {
            if (_selectedFiles.value.isEmpty()) {
                NotificationManager.showWarning("No files selected")
                return@launch
            }

            val manager = exportManager ?: run {
                NotificationManager.showError("Export manager not initialized")
                return@launch
            }

            _isExporting.value = true
            try {
                val selectedFileList = _files.value.filter {
                    _selectedFiles.value.contains(it.name)
                }

                val result = manager.exportFiles(selectedFileList)

                if (result.success) {
                    NotificationManager.showSuccess(
                        "Successfully exported ${result.exportedCount} file(s)"
                    )
                    _selectedFiles.value = emptySet()

                    if (result.exportedFiles.isNotEmpty() &&
                        AppPreferences.isAutoShareExportEnabled(context)) {
                        manager.shareFiles(result.exportedFiles)
                    }
                } else {
                    NotificationManager.showError("Export failed: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                NotificationManager.showError("Export failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun deleteFile(fileName: String) {
        viewModelScope.launch {
            val manager = exportManager ?: return@launch
            val result = manager.deleteFile(fileName)
            if (result.success) {
                NotificationManager.showSuccess("File deleted")
                loadFiles()
            } else {
                NotificationManager.showError("Delete failed: ${result.errorMessage}")
            }
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            if (_selectedFiles.value.isEmpty()) {
                NotificationManager.showWarning("No files selected")
                return@launch
            }

            val manager = exportManager ?: return@launch
            val result = manager.deleteFiles(_selectedFiles.value)
            if (result.success) {
                NotificationManager.showSuccess("Deleted ${result.exportedCount} file(s)")
                _selectedFiles.value = emptySet()
                loadFiles()
            } else {
                NotificationManager.showError("Delete failed: ${result.errorMessage}")
            }
        }
    }
}