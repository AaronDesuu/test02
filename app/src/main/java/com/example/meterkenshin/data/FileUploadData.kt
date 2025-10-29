package com.example.meterkenshin.data

import android.annotation.SuppressLint
import android.net.Uri

/**
 * Represents the overall state of the file upload process
 */
data class FileUploadState(
    val requiredFiles: List<RequiredFile> = emptyList(),
    val isUploading: Boolean = false
) {
    val uploadedFilesCount: Int
        get() = requiredFiles.count { it.status == FileStatus.UPLOADED }

    val allFilesUploaded: Boolean
        get() = requiredFiles.isNotEmpty() && requiredFiles.all { it.status == FileStatus.UPLOADED }

    enum class FileStatus {
        PENDING,    // No file selected
        SELECTED,   // File selected but not uploaded
        UPLOADING,  // Currently uploading
        UPLOADED,   // Successfully uploaded
        ERROR       // Upload failed
    }
}

/**
 * Represents a required file for the application
 */
data class RequiredFile(
    val type: FileType,
    val displayName: String,
    val description: String,
    val fileName: String,
    val status: FileUploadState.FileStatus = FileUploadState.FileStatus.PENDING,
    val selectedUri: Uri? = null,
    val selectedFileName: String? = null,
    val fileSize: Long = 0L,
    val uploadProgress: Int = 0,
    val errorMessage: String? = null,
    val uploadedAt: Long? = null
) {
    enum class FileType {
        METER,
        PRINTER,
        RATE
    }

    val isUploaded: Boolean
        get() = status == FileUploadState.FileStatus.UPLOADED

    val formattedFileSize: String
        @SuppressLint("DefaultLocale")
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
        }
}
