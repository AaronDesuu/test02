package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.ui.manager.ExportManager
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

    fun loadFiles() {
        viewModelScope.launch {
            try {
                val filesList = exportManager.getAvailableFiles()
                _files.value = filesList
            } catch (e: Exception) {
                NotificationManager.showError("Failed to load files: ${e.message}")
            }
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
}