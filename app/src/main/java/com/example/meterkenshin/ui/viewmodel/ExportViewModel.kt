package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.ui.manager.ExportManager
import com.example.meterkenshin.ui.manager.FileGroup
import com.example.meterkenshin.ui.manager.NotificationManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val exportManager = ExportManager(application)

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    // Add to ExportViewModel
    private val _fileGroups = MutableStateFlow<List<FileGroup>>(emptyList())

    // Add search functionality
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

    fun loadFiles() {
        viewModelScope.launch {
            _fileGroups.value = exportManager.getGroupedFiles()
            _files.value = exportManager.getAvailableFiles()
            filterFiles() // Initialize filtered groups
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

    fun exportSelectedFiles() {
        viewModelScope.launch {
            if (_selectedFiles.value.isEmpty()) {
                NotificationManager.showWarning("No files selected")
                return@launch
            }

            _isExporting.value = true
            try {
                val selectedFileList = _files.value.filter {
                    _selectedFiles.value.contains(it.name)
                }

                val result = exportManager.exportFiles(selectedFileList)

                if (result.success) {
                    NotificationManager.showSuccess(
                        "Successfully exported ${result.exportedCount} file(s) to Download/kenshinApp"
                    )
                    _selectedFiles.value = emptySet()
                } else {
                    NotificationManager.showError(
                        "Export failed: ${result.errorMessage}"
                    )
                }
            } catch (e: Exception) {
                NotificationManager.showError("Export failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    // New delete methods
    fun deleteFile(fileName: String) {
        viewModelScope.launch {
            try {
                val result = exportManager.deleteFile(fileName)
                if (result.success) {
                    // Remove from selection if it was selected
                    val current = _selectedFiles.value.toMutableSet()
                    current.remove(fileName)
                    _selectedFiles.value = current

                    // Reload files
                    loadFiles()
                    NotificationManager.showSuccess("File '$fileName' deleted successfully")
                } else {
                    NotificationManager.showError("Failed to delete file: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                NotificationManager.showError("Error deleting file: ${e.message}")
            }
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            if (_selectedFiles.value.isEmpty()) {
                NotificationManager.showWarning("No files selected")
                return@launch
            }

            try {
                val selectedFileNames = _selectedFiles.value.toList()
                val result = exportManager.deleteFiles(selectedFileNames)

                if (result.success) {
                    _selectedFiles.value = emptySet()
                    loadFiles()
                    NotificationManager.showSuccess(
                        "Successfully deleted ${result.exportedCount} file(s)"
                    )
                } else {
                    NotificationManager.showError(
                        "Failed to delete files: ${result.errorMessage}"
                    )
                }
            } catch (e: Exception) {
                NotificationManager.showError("Error deleting files: ${e.message}")
            }
        }
    }
}