package com.example.meterkenshin.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.R
import com.example.meterkenshin.data.FileUploadState
import com.example.meterkenshin.data.RequiredFile
import com.example.meterkenshin.utils.getCurrentYearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.text.format

class FileUploadViewModel : ViewModel() {
    private val _uploadState = MutableStateFlow(FileUploadState())
    val uploadState: StateFlow<FileUploadState> = _uploadState.asStateFlow()

    companion object {
        private const val TAG = "FileUploadViewModel"
        private const val MAX_FILE_SIZE_MB = 512
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024
        private const val APP_FILES_FOLDER = "app_files"
    }

    init {
        initializeRequiredFiles()
    }

    /**
     * Initialize the required files and check for existing files in app storage
     */
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

    /**
     * Check for existing files in app storage and update the state
     */
    fun checkExistingFiles(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val appFilesDir = getAppFilesDirectory(context)

                val updatedFiles = _uploadState.value.requiredFiles.map { file ->
                    val existingFile = File(appFilesDir, file.fileName)
                    if (existingFile.exists() && existingFile.isFile) {
                        file.copy(
                            status = FileUploadState.FileStatus.UPLOADED,
                            fileSize = existingFile.length(),
                            uploadedAt = existingFile.lastModified(),
                            uploadProgress = 100
                        )
                    } else {
                        file
                    }
                }

                withContext(Dispatchers.Main) {
                    _uploadState.value = _uploadState.value.copy(
                        requiredFiles = updatedFiles
                    )
                    Log.i(TAG, "Checked existing files. Found ${updatedFiles.count { it.isUploaded }} uploaded files.")
                }
            }
        }
    }

    /**
     * Select a file for upload
     */
    fun selectFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri)
                val fileSize = getFileSize(context, uri)

                // Validate file
                val validationResult = validateFile(fileName, fileSize, context)
                if (!validationResult.isValid) {
                    Log.w(TAG, "File validation failed: ${validationResult.errorMessage}")
                    updateUploadError(validationResult.errorMessage ?: context.getString(R.string.file_validation_failed))
                    return@launch
                }

                // Determine file type based on name
                val fileType = determineFileType(fileName)
                if (fileType == null) {
                    Log.w(TAG, "Could not determine file type for: $fileName")
                    updateUploadError(context.getString(R.string.file_type_not_recognized))
                    return@launch
                }

                // Update the corresponding file
                updateFileSelection(fileType, uri, fileName, fileSize)
                Log.i(TAG, "File selected: $fileName (${formatFileSize(fileSize)}) for type: $fileType")

            } catch (e: Exception) {
                Log.e(TAG, "Error selecting file", e)
                updateUploadError(e.localizedMessage ?: context.getString(R.string.file_selection_failed))
            }
        }
    }

    /**
     * Upload the selected file and copy it to app storage
     */
    fun uploadFile(fileType: RequiredFile.FileType, context: Context) {
        viewModelScope.launch {
            val currentFile = _uploadState.value.requiredFiles.find { it.type == fileType }
            if (currentFile?.selectedUri == null) {
                Log.w(TAG, "No file selected for upload: $fileType")
                updateUploadError(context.getString(R.string.no_file_selected))
                return@launch
            }

            try {
                // Update status to uploading
                updateFileStatus(fileType, FileUploadState.FileStatus.UPLOADING)
                Log.i(TAG, "Starting upload for: ${currentFile.fileName}")

                // Perform actual file upload with progress
                val success = performFileUpload(currentFile, context) { progress ->
                    updateFileProgress(fileType, progress)
                }

                if (success) {
                    updateFileStatus(fileType, FileUploadState.FileStatus.UPLOADED)
                    updateFileUploadedAt(fileType, System.currentTimeMillis())
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

    /**
     * Remove a file (clear selection and delete from storage if uploaded)
     */
    fun removeFile(fileType: RequiredFile.FileType, context: Context) {
        viewModelScope.launch {
            try {
                // Delete from app storage if it exists
                deleteExistingFile(fileType, context)

                // Reset the file state
                updateFileSelection(fileType, null, null, 0)
                updateFileStatus(fileType, FileUploadState.FileStatus.PENDING)

                Log.i(TAG, "File removed: $fileType")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing file", e)
                updateUploadError(e.localizedMessage ?: context.getString(R.string.file_removal_failed))
            }
        }
    }

    /**
     * Delete an existing file from app storage
     */
    private suspend fun deleteExistingFile(fileType: RequiredFile.FileType, context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currentFile = _uploadState.value.requiredFiles.find { it.type == fileType }
                if (currentFile == null) return@withContext false

                val appFilesDir = getAppFilesDirectory(context)
                val existingFile = File(appFilesDir, currentFile.fileName)

                if (existingFile.exists()) {
                    val deleted = existingFile.delete()
                    if (deleted) {
                        Log.i(TAG, "Deleted existing file: ${existingFile.absolutePath}")
                    } else {
                        Log.w(TAG, "Failed to delete existing file: ${existingFile.absolutePath}")
                    }
                    deleted
                } else {
                    Log.d(TAG, "No existing file to delete: ${existingFile.absolutePath}")
                    true // Consider it success if file doesn't exist
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting existing file", e)
                false
            }
        }
    }

    /**
     * Perform the actual file upload operation
     */
    private suspend fun performFileUpload(
        file: RequiredFile,
        context: Context,
        onProgress: (Int) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val appFilesDir = getAppFilesDirectory(context)
                val targetFile = File(appFilesDir, file.fileName)

                // Ensure parent directory exists
                if (!appFilesDir.exists()) {
                    appFilesDir.mkdirs()
                }

                // Copy file with progress reporting
                context.contentResolver.openInputStream(file.selectedUri!!)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        val totalSize = file.fileSize

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // Report progress
                            if (totalSize > 0) {
                                val progress = (totalBytesRead * 100 / totalSize).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress.coerceIn(0, 100))
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }

                // Verify file was written successfully
                val success = targetFile.exists() && targetFile.length() > 0
                if (success) {
                    Log.i(TAG, "File copied successfully to: ${targetFile.absolutePath} (${formatFileSize(targetFile.length())})")

                    // If the uploaded file is meter.csv, create a date-stamped copy
                    if (file.type == RequiredFile.FileType.METER && file.fileName == "meter.csv") {
                        val yearMonth = getCurrentYearMonth() // e.g., "202410"
                        val datedFileName = "${yearMonth}_meter.csv"
                        val datedFile = File(appFilesDir, datedFileName)

                        targetFile.copyTo(datedFile, overwrite = true)
                        Log.i(TAG, "Created date-stamped copy: ${datedFile.absolutePath}")
                    }
                }
                success

            } catch (e: Exception) {
                Log.e(TAG, "Error performing file upload", e)
                false
            }
        }

    }

    /**
     * Get or create the app files directory
     */
    private fun getAppFilesDirectory(context: Context): File {
        val externalFilesDir = context.getExternalFilesDir(null)
        return if (externalFilesDir != null) {
            File(externalFilesDir, APP_FILES_FOLDER)
        } else {
            // Fallback to internal storage
            File(context.filesDir, APP_FILES_FOLDER)
        }
    }

    /**
     * Update file selection state
     */
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

    /**
     * Update file status
     */
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

    /**
     * Update file upload progress
     */
    private fun updateFileProgress(fileType: RequiredFile.FileType, progress: Int) {
        val updatedFiles = _uploadState.value.requiredFiles.map { file ->
            if (file.type == fileType) {
                file.copy(uploadProgress = progress.coerceIn(0, 100))
            } else {
                file
            }
        }

        _uploadState.value = _uploadState.value.copy(
            requiredFiles = updatedFiles
        )
    }

    /**
     * Update file uploaded timestamp
     */
    private fun updateFileUploadedAt(fileType: RequiredFile.FileType, timestamp: Long) {
        val updatedFiles = _uploadState.value.requiredFiles.map { file ->
            if (file.type == fileType) {
                file.copy(uploadedAt = timestamp)
            } else {
                file
            }
        }

        _uploadState.value = _uploadState.value.copy(
            requiredFiles = updatedFiles
        )
    }

    /**
     * Update general upload error
     */
    private fun updateUploadError(errorMessage: String) {
        // You might want to add a general error state to FileUploadState
        Log.e(TAG, "Upload error: $errorMessage")
    }

    /**
     * Validate the selected file
     */
    private fun validateFile(fileName: String?, fileSize: Long, context: Context): ValidationResult {
        if (fileName == null) {
            return ValidationResult(false, context.getString(R.string.invalid_file_name))
        }

        if (fileSize <= 0) {
            return ValidationResult(false, context.getString(R.string.empty_file))
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return ValidationResult(false, context.getString(R.string.file_too_large, MAX_FILE_SIZE_MB))
        }

        if (!fileName.endsWith(".csv", ignoreCase = true)) {
            return ValidationResult(false, context.getString(R.string.invalid_file_type))
        }

        return ValidationResult(true)
    }

    /**
     * Determine file type based on filename
     */
    private fun determineFileType(fileName: String?): RequiredFile.FileType? {
        if (fileName == null) return null

        return when {
            fileName.contains("meter", ignoreCase = true) -> RequiredFile.FileType.METER
            fileName.contains("printer", ignoreCase = true) -> RequiredFile.FileType.PRINTER
            fileName.contains("rate", ignoreCase = true) -> RequiredFile.FileType.RATE
            else -> null
        }
    }

    /**
     * Get filename from URI
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        }

        if (fileName == null) {
            fileName = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }

        return fileName
    }

    /**
     * Get file size from URI
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        return size
    }

    /**
     * Format file size for display
     */
    @SuppressLint("DefaultLocale")
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Data class for validation results
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}