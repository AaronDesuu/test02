package com.example.meterkenshin.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.meterkenshin.R
import com.example.meterkenshin.model.FileUploadResult
import com.example.meterkenshin.model.RequiredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Manages file upload operations including validation, copying, and storage
 */
class FileUploadManager {

    companion object {
        private const val TAG = "FileUploadManager"
        private const val BUFFER_SIZE = 8192
        private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    }

    /**
     * Uploads a file from URI to the app's external files directory
     */
    suspend fun uploadFile(
        context: Context,
        uri: Uri,
        targetFileName: String,
        onProgress: ((Int) -> Unit)? = null
    ): FileUploadResult = withContext(Dispatchers.IO) {
        try {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir == null) {
                Log.e(TAG, "External storage not available")
                return@withContext FileUploadResult(
                    success = false,
                    errorMessage = context.getString(R.string.upload_failed)
                )
            }

            val targetFile = File(externalFilesDir, targetFileName)

            // Get file size for progress calculation
            val fileSize = getFileSize(context, uri)
            if (fileSize <= 0) {
                return@withContext FileUploadResult(
                    success = false,
                    errorMessage = context.getString(R.string.file_empty)
                )
            }

            // Validate file size
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return@withContext FileUploadResult(
                    success = false,
                    errorMessage = context.getString(R.string.file_too_large, MAX_FILE_SIZE_BYTES / (1024 * 1024))
                )
            }

            // Copy file with progress tracking
            val success = copyFileWithProgress(context, uri, targetFile, fileSize, onProgress)

            if (success) {
                Log.i(TAG, "File uploaded successfully: ${targetFile.absolutePath}")
                FileUploadResult(
                    success = true,
                    fileName = targetFileName,
                    uploadedFilePath = targetFile.absolutePath
                )
            } else {
                FileUploadResult(
                    success = false,
                    errorMessage = context.getString(R.string.upload_failed)
                )
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during file upload", e)
            FileUploadResult(
                success = false,
                errorMessage = context.getString(R.string.permission_denied)
            )
        } catch (e: IOException) {
            Log.e(TAG, "IO exception during file upload", e)
            FileUploadResult(
                success = false,
                errorMessage = context.getString(R.string.upload_failed)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during file upload", e)
            FileUploadResult(
                success = false,
                errorMessage = e.localizedMessage ?: context.getString(R.string.upload_failed)
            )
        }
    }

    /**
     * Validates a file before upload
     */
    fun validateFile(context: Context, uri: Uri, fileName: String?): ValidationResult {
        if (fileName == null || fileName.isEmpty()) {
            return ValidationResult(false, context.getString(R.string.invalid_file_type))
        }

        // Check file extension
        if (!fileName.lowercase().endsWith(".csv")) {
            return ValidationResult(false, context.getString(R.string.invalid_file_type))
        }

        // Check file size
        val fileSize = getFileSize(context, uri)
        if (fileSize <= 0) {
            return ValidationResult(false, context.getString(R.string.file_empty))
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return ValidationResult(
                false,
                context.getString(R.string.file_too_large, MAX_FILE_SIZE_BYTES / (1024 * 1024))
            )
        }

        return ValidationResult(true)
    }

    /**
     * Checks if all required files are uploaded
     */
    fun areAllFilesUploaded(context: Context, requiredFileNames: List<String>): Boolean {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return false

        return requiredFileNames.all { fileName ->
            val file = File(externalFilesDir, fileName)
            file.exists() && file.length() > 0
        }
    }

    /**
     * Gets the list of missing files
     */
    fun getMissingFiles(context: Context, requiredFiles: List<RequiredFile>): List<RequiredFile> {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return requiredFiles

        return requiredFiles.filter { requiredFile ->
            val file = File(externalFilesDir, requiredFile.fileName)
            !file.exists() || file.length() == 0L
        }
    }

    /**
     * Deletes an uploaded file
     */
    suspend fun deleteFile(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val externalFilesDir = context.getExternalFilesDir(null) ?: return@withContext false
            val file = File(externalFilesDir, fileName)

            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.i(TAG, "File deleted successfully: $fileName")
                } else {
                    Log.w(TAG, "Failed to delete file: $fileName")
                }
                deleted
            } else {
                Log.w(TAG, "File does not exist for deletion: $fileName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $fileName", e)
            false
        }
    }

    /**
     * Gets file information from URI
     */
    fun getFileInfo(context: Context, uri: Uri): FileInfo? {
        return try {
            val fileName = getFileName(context, uri)
            val fileSize = getFileSize(context, uri)
            val mimeType = context.contentResolver.getType(uri)

            if (fileName != null) {
                FileInfo(
                    name = fileName,
                    size = fileSize,
                    mimeType = mimeType,
                    extension = getFileExtension(fileName)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file info", e)
            null
        }
    }

    /**
     * Copies file from URI to target file with progress tracking
     */
    private suspend fun copyFileWithProgress(
        context: Context,
        sourceUri: Uri,
        targetFile: File,
        totalSize: Long,
        onProgress: ((Int) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalBytesRead = 0L
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        onProgress?.let { callback ->
                            val progress = ((totalBytesRead.toDouble() / totalSize.toDouble()) * 100).toInt()
                            withContext(Dispatchers.Main) {
                                callback(progress)
                            }
                        }
                    }

                    outputStream.flush()
                    true
                }
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file", e)
            false
        }
    }

    /**
     * Gets file name from URI
     */
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

    /**
     * Gets file size from URI
     */
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

    /**
     * Gets file extension from filename
     */
    private fun getFileExtension(fileName: String): String? {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            null
        }
    }

    /**
     * Calculates MD5 hash of a file for integrity checking
     */
    suspend fun calculateFileHash(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }

                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating file hash", e)
            null
        }
    }

    /**
     * Data class for file information
     */
    data class FileInfo(
        val name: String,
        val size: Long,
        val mimeType: String?,
        val extension: String?
    ) {
        val formattedSize: String
            get() = when {
                size < 1024 -> "${size} B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            }
    }

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}