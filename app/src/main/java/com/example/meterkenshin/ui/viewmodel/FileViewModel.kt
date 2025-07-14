package com.example.meterkenshin.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.R
import com.example.meterkenshin.model.FileUploadState
import com.example.meterkenshin.model.RequiredFile
import com.example.meterkenshin.utils.FileUploadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileUploadViewModel : ViewModel() {
    private val _uploadState = MutableStateFlow(FileUploadState())
    val uploadState: StateFlow<FileUploadState> = _uploadState.asStateFlow()

    private val fileUploadManager = FileUploadManager()

    companion object {
        private const val TAG = "FileUploadViewModel"
        private const val MAX_FILE_SIZE_MB = 10
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024
    }

    init {
        initializeRequiredFiles()
    }

    private fun initializeRequiredFiles() {
        val requiredFiles = listOf(
            RequiredFile(
                type = RequiredFile.FileType.METER,
                displayName = "Meter CSV File",
                description = "Contains meter configuration and metadata",
                fileName = "meter.csv",
                status = FileUploadState.FileStatus.PENDING
            ),
            RequiredFile(
                type = RequiredFile.FileType.PRINTER,
                displayName = "Printer CSV File",
                description = "Contains printer settings and configurations",
                fileName = "printer.csv",
                status = FileUploadState.FileStatus.PENDING
            ),
            RequiredFile(
                type = RequiredFile.FileType.RATE,
                displayName = "Rate CSV File",
                description = "Contains rate tables and calculation parameters",
                fileName = "rate.csv",
                status = FileUploadState.FileStatus.PENDING
            )
        )

        _uploadState.value = _uploadState.value.copy(
            requiredFiles = requiredFiles
        )
    }

    fun selectFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri)
                val fileSize = getFileSize(context, uri)

                // Validate file
                val validationResult = validateFile(fileName, fileSize, context)
                if (!validationResult.isValid) {
                    Log.w(TAG, "File validation failed: ${validationResult.errorMessage}")
                    return@launch
                }

                // Determine file type based on name or let user choose
                val fileType = determineFileType(fileName)
                if (fileType == null) {
                    Log.w(TAG, "Could not determine file type for: $fileName")
                    // In a real app, you might show a dialog to let user choose the file type
                    return@launch
                }

                // Update the corresponding file
                updateFileSelection(fileType, uri, fileName, fileSize)

            } catch (e: Exception) {
                Log.e(TAG, "Error selecting file", e)
            }
        }
    }

    fun uploadFile(fileType: RequiredFile.FileType, context: Context) {
        viewModelScope.launch {
            val currentFile = _uploadState.value.requiredFiles.find { it.type == fileType }
            if (currentFile?.selectedUri == null) {
                Log.w(TAG, "No file selected for upload: $fileType")
                return@launch
            }

            try {
                // Update status to uploading
                updateFileStatus(fileType, FileUploadState.FileStatus.UPLOADING)

                // Simulate upload progress
                for (progress in 0..100 step 10) {
                    updateFileProgress(fileType, progress)
                    kotlinx.coroutines.delay(100) // Simulate upload time
                }

                // Perform actual file upload
                val success = performFileUpload(currentFile, context)

                if (success) {
                    updateFileStatus(fileType, FileUploadState.FileStatus.UPLOADED)
                    Log.i(TAG, "File uploaded successfully: ${currentFile.fileName}")
                } else {
                    updateFileStatus(
                        fileType,
                        FileUploadState.FileStatus.ERROR,
                        errorMessage = context.getString(R.string.upload_failed)
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file", e)
                updateFileStatus(
                    fileType,
                    FileUploadState.FileStatus.ERROR,
                    errorMessage = e.localizedMessage ?: context.getString(R.string.upload_failed)
                )
            }
        }
    }

    fun removeFile(fileType: RequiredFile.FileType) {
        updateFileSelection(fileType, null, null, 0)
        updateFileStatus(fileType, FileUploadState.FileStatus.PENDING)
    }

    private fun updateFileSelection(
        fileType: RequiredFile.FileType,
        uri: Uri?,
        fileName: String?,
        fileSize: Long
    ) {
        val updatedFiles = _uploadState.value.requiredFiles.map { file ->
            if (file.type == fileType) {
                file.copy(
                    selectedUri = uri,
                    selectedFileName = fileName,
                    fileSize = fileSize,
                    status = if (uri != null) FileUploadState.FileStatus.SELECTED else FileUploadState.FileStatus.PENDING,
                    uploadProgress = 0,
                    errorMessage = null
                )
            } else {
                file
            }
        }

        _uploadState.value = _uploadState.value.copy(
            requiredFiles = updatedFiles
        )
    }

    private fun updateFileStatus(
        fileType: RequiredFile.FileType,
        status: FileUploadState.FileStatus,
        errorMessage: String? = null
    ) {
        val updatedFiles = _uploadState.value.requiredFiles.map { file ->
            if (file.type == fileType) {
                file.copy(
                    status = status,
                    errorMessage = errorMessage,
                    uploadProgress = if (status == FileUploadState.FileStatus.UPLOADED) 100 else file.uploadProgress
                )
            } else {
                file
            }
        }

        _uploadState.value = _uploadState.value.copy(
            requiredFiles = updatedFiles
        )
    }

    private fun updateFileProgress(fileType: RequiredFile.FileType, progress: Int) {
        val updatedFiles = _uploadState.value.requiredFiles.map { file ->
            if (file.type == fileType) {
                file.copy(uploadProgress = progress)
            } else {
                file
            }
        }

        _uploadState.value = _uploadState.value.copy(
            requiredFiles = updatedFiles
        )
    }

    private suspend fun performFileUpload(file: RequiredFile, context: Context): Boolean {
        return try {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir == null) {
                Log.e(TAG, "External storage not available")
                return false
            }

            val targetFile = File(externalFilesDir, file.fileName)

            context.contentResolver.openInputStream(file.selectedUri!!)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Verify file was written successfully
            targetFile.exists() && targetFile.length() > 0

        } catch (e: Exception) {
            Log.e(TAG, "Error performing file upload", e)
            false
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        }

        if (fileName == null) {
            val path = uri.path
            if (path != null) {
                val cut = path.lastIndexOf('/')
                if (cut != -1) {
                    fileName = path.substring(cut + 1)
                }
            }
        }

        return fileName
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var fileSize = 0L

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        return fileSize
    }

    private fun validateFile(fileName: String?, fileSize: Long, context: Context): ValidationResult {
        if (fileName == null) {
            return ValidationResult(false, context.getString(R.string.invalid_file_type))
        }

        if (!fileName.endsWith(".csv", ignoreCase = true)) {
            return ValidationResult(false, context.getString(R.string.invalid_file_type))
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return ValidationResult(false, context.getString(R.string.file_too_large, MAX_FILE_SIZE_MB))
        }

        if (fileSize == 0L) {
            return ValidationResult(false, context.getString(R.string.file_empty))
        }

        return ValidationResult(true)
    }

    private fun determineFileType(fileName: String?): RequiredFile.FileType? {
        if (fileName == null) return null

        return when {
            fileName.contains("meter", ignoreCase = true) -> RequiredFile.FileType.METER
            fileName.contains("printer", ignoreCase = true) -> RequiredFile.FileType.PRINTER
            fileName.contains("rate", ignoreCase = true) -> RequiredFile.FileType.RATE
            else -> null // In a real app, you might ask the user to specify
        }
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}