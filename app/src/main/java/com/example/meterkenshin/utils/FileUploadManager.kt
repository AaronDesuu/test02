package com.example.meterkenshin.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.WorkerThread
import com.example.meterkenshin.model.RequiredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest

/**
 * Utility class for managing file operations including copying, validation, and storage management
 */
class FileManager private constructor() {

    companion object {
        private const val TAG = "FileManager"
        private const val APP_FILES_FOLDER = "app_files"
        private const val BUFFER_SIZE = 8192
        private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB

        @Volatile
        private var INSTANCE: FileManager? = null

        fun getInstance(): FileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * Get the app files directory, creating it if necessary
     */
    fun getAppFilesDirectory(context: Context): File {
        val externalFilesDir = context.getExternalFilesDir(null)
        val appFilesDir = if (externalFilesDir != null) {
            File(externalFilesDir, APP_FILES_FOLDER)
        } else {
            // Fallback to internal storage
            File(context.filesDir, APP_FILES_FOLDER)
        }

        if (!appFilesDir.exists()) {
            appFilesDir.mkdirs()
            Log.d(TAG, "Created app files directory: ${appFilesDir.absolutePath}")
        }

        return appFilesDir
    }

    /**
     * Check if a file exists in the app storage
     */
    fun fileExists(context: Context, fileName: String): Boolean {
        val appFilesDir = getAppFilesDirectory(context)
        val file = File(appFilesDir, fileName)
        return file.exists() && file.isFile()
    }

    /**
     * Get file information for an existing file in app storage
     */
    fun getFileInfo(context: Context, fileName: String): FileInfo? {
        val appFilesDir = getAppFilesDirectory(context)
        val file = File(appFilesDir, fileName)

        return if (file.exists() && file.isFile()) {
            FileInfo(
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified(),
                path = file.absolutePath
            )
        } else {
            null
        }
    }

    /**
     * Copy a file from URI to app storage with progress callback
     */
    @WorkerThread
    suspend fun copyFileToAppStorage(
        context: Context,
        sourceUri: Uri,
        targetFileName: String,
        onProgress: ((Int) -> Unit)? = null
    ): CopyResult {
        return withContext(Dispatchers.IO) {
            try {
                val appFilesDir = getAppFilesDirectory(context)
                val targetFile = File(appFilesDir, targetFileName)

                // Get source file size for progress calculation
                val sourceSize = getFileSize(context, sourceUri)

                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    BufferedInputStream(inputStream).use { bufferedInput ->
                        FileOutputStream(targetFile).use { outputStream ->
                            BufferedOutputStream(outputStream).use { bufferedOutput ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytesRead: Int
                                var totalBytesRead = 0L

                                while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
                                    bufferedOutput.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead

                                    // Report progress
                                    if (sourceSize > 0 && onProgress != null) {
                                        val progress = (totalBytesRead * 100 / sourceSize).toInt()
                                        withContext(Dispatchers.Main) {
                                            onProgress(progress.coerceIn(0, 100))
                                        }
                                    }
                                }
                                bufferedOutput.flush()
                            }
                        }
                    }
                }

                // Verify the copy was successful
                if (targetFile.exists() && targetFile.length() > 0) {
                    val fileInfo = FileInfo(
                        name = targetFile.name,
                        size = targetFile.length(),
                        lastModified = targetFile.lastModified(),
                        path = targetFile.absolutePath
                    )
                    Log.i(TAG, "File copied successfully: ${targetFile.absolutePath} (${formatFileSize(targetFile.length())})")
                    CopyResult.Success(fileInfo)
                } else {
                    Log.e(TAG, "File copy verification failed: ${targetFile.absolutePath}")
                    CopyResult.Error("File copy verification failed")
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during file copy", e)
                CopyResult.Error("Permission denied: ${e.localizedMessage}")
            } catch (e: IOException) {
                Log.e(TAG, "IO exception during file copy", e)
                CopyResult.Error("File copy failed: ${e.localizedMessage}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during file copy", e)
                CopyResult.Error("Unexpected error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Delete a file from app storage
     */
    @WorkerThread
    suspend fun deleteFile(context: Context, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val appFilesDir = getAppFilesDirectory(context)
                val file = File(appFilesDir, fileName)

                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.i(TAG, "File deleted successfully: ${file.absolutePath}")
                    } else {
                        Log.w(TAG, "Failed to delete file: ${file.absolutePath}")
                    }
                    deleted
                } else {
                    Log.d(TAG, "File does not exist, considering deletion successful: ${file.absolutePath}")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting file: $fileName", e)
                false
            }
        }
    }

    /**
     * Get all files in the app storage directory
     */
    fun getAllAppFiles(context: Context): List<FileInfo> {
        val appFilesDir = getAppFilesDirectory(context)

        return try {
            appFilesDir.listFiles()
                ?.filter { it.isFile() }
                ?.map { file ->
                    FileInfo(
                        name = file.name,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        path = file.absolutePath
                    )
                }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing app files", e)
            emptyList()
        }
    }

    /**
     * Clean up old or invalid files
     */
    @WorkerThread
    suspend fun cleanupFiles(context: Context, validFileNames: Set<String>): Int {
        return withContext(Dispatchers.IO) {
            var deletedCount = 0
            try {
                val appFilesDir = getAppFilesDirectory(context)
                val allFiles = appFilesDir.listFiles()?.filter { it.isFile() } ?: emptyList()

                for (file in allFiles) {
                    if (file.name !in validFileNames) {
                        if (file.delete()) {
                            deletedCount++
                            Log.d(TAG, "Cleaned up invalid file: ${file.name}")
                        } else {
                            Log.w(TAG, "Failed to delete invalid file: ${file.name}")
                        }
                    }
                }

                Log.i(TAG, "Cleanup completed: $deletedCount files deleted")
            } catch (e: Exception) {
                Log.e(TAG, "Error during file cleanup", e)
            }
            deletedCount
        }
    }

    /**
     * Validate file from URI
     */
    fun validateFile(context: Context, uri: Uri): ValidationResult {
        try {
            val fileName = getFileName(context, uri)
            if (fileName == null) {
                return ValidationResult(false, "Could not determine file name")
            }

            if (!fileName.endsWith(".csv", ignoreCase = true)) {
                return ValidationResult(false, "Only CSV files are supported")
            }

            val fileSize = getFileSize(context, uri)
            if (fileSize <= 0) {
                return ValidationResult(false, "File is empty or size could not be determined")
            }

            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return ValidationResult(false, "File size exceeds ${MAX_FILE_SIZE_BYTES / (1024 * 1024)}MB limit")
            }

            // Check if file is accessible
            context.contentResolver.openInputStream(uri)?.use {
                // Try to read a small amount to verify file is readable
                val buffer = ByteArray(1024)
                it.read(buffer)
            }

            return ValidationResult(true)

        } catch (e: SecurityException) {
            return ValidationResult(false, "Permission denied to access file")
        } catch (e: IOException) {
            return ValidationResult(false, "File could not be read")
        } catch (e: Exception) {
            return ValidationResult(false, "File validation failed: ${e.localizedMessage}")
        }
    }

    /**
     * Get file name from URI
     */
    fun getFileName(context: Context, uri: Uri): String? {
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
    fun getFileSize(context: Context, uri: Uri): Long {
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
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Calculate MD5 hash of a file
     */
    @WorkerThread
    suspend fun calculateFileHash(context: Context, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val appFilesDir = getAppFilesDirectory(context)
                val file = File(appFilesDir, fileName)

                if (!file.exists()) return@withContext null

                val md5 = MessageDigest.getInstance("MD5")
                FileInputStream(file).use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                            md5.update(buffer, 0, bytesRead)
                        }
                    }
                }

                val hashBytes = md5.digest()
                hashBytes.joinToString("") { "%02x".format(it) }

            } catch (e: Exception) {
                Log.e(TAG, "Error calculating file hash for: $fileName", e)
                null
            }
        }
    }

    /**
     * Determine file type based on filename
     */
    fun determineFileType(fileName: String?): RequiredFile.FileType? {
        if (fileName == null) return null

        return when {
            fileName.contains("meter", ignoreCase = true) -> RequiredFile.FileType.METER
            fileName.contains("printer", ignoreCase = true) -> RequiredFile.FileType.PRINTER
            fileName.contains("rate", ignoreCase = true) -> RequiredFile.FileType.RATE
            else -> null
        }
    }

    /**
     * Get storage space information
     */
    fun getStorageInfo(context: Context): StorageInfo {
        val appFilesDir = getAppFilesDirectory(context)

        return try {
            val totalSpace = appFilesDir.totalSpace
            val freeSpace = appFilesDir.freeSpace
            val usedSpace = totalSpace - freeSpace

            StorageInfo(
                totalSpace = totalSpace,
                freeSpace = freeSpace,
                usedSpace = usedSpace,
                isLowOnSpace = freeSpace < (50 * 1024 * 1024) // Less than 50MB
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage info", e)
            StorageInfo(0, 0, 0, false)
        }
    }

    /**
     * Check if there's enough space for a file
     */
    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        val storageInfo = getStorageInfo(context)
        return storageInfo.freeSpace > (requiredBytes + (10 * 1024 * 1024)) // Add 10MB buffer
    }
}

/**
 * Data class representing file information
 */
data class FileInfo(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val path: String
) {
    val formattedSize: String
        get() = FileManager.getInstance().formatFileSize(size)
}

/**
 * Sealed class representing the result of a file copy operation
 */
sealed class CopyResult {
    data class Success(val fileInfo: FileInfo) : CopyResult()
    data class Error(val message: String) : CopyResult()
}

/**
 * Data class representing file validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * Data class representing storage information
 */
data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val isLowOnSpace: Boolean
) {
    val formattedTotalSpace: String
        get() = FileManager.getInstance().formatFileSize(totalSpace)

    val formattedFreeSpace: String
        get() = FileManager.getInstance().formatFileSize(freeSpace)

    val formattedUsedSpace: String
        get() = FileManager.getInstance().formatFileSize(usedSpace)
}