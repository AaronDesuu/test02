package com.example.meterkenshin.model

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

    val hasIncompleteUploads: Boolean
        get() = requiredFiles.any { it.status in listOf(FileStatus.PENDING, FileStatus.SELECTED, FileStatus.ERROR) }

    val missingFiles: List<String>
        get() = requiredFiles
            .filter { it.status != FileStatus.UPLOADED }
            .map { when (it.type) {
                RequiredFile.FileType.METER -> "Meter CSV file is missing"
                RequiredFile.FileType.PRINTER -> "Printer CSV file is missing"
                RequiredFile.FileType.RATE -> "Rate CSV file is missing"
            }}

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

    val isSelected: Boolean
        get() = selectedUri != null

    val isUploaded: Boolean
        get() = status == FileUploadState.FileStatus.UPLOADED

    val formattedFileSize: String
        get() = when {
            fileSize < 1024 -> "${fileSize} B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
        }
}

/**
 * Represents the result of a file upload operation
 */
data class FileUploadResult(
    val success: Boolean,
    val fileName: String? = null,
    val errorMessage: String? = null,
    val uploadedFilePath: String? = null
)

/**
 * Configuration for file upload constraints
 */
data class FileUploadConfig(
    val maxFileSizeMB: Int = 10,
    val allowedExtensions: List<String> = listOf("csv"),
    val requiredFiles: List<RequiredFileConfig> = listOf(
        RequiredFileConfig(
            type = RequiredFile.FileType.METER,
            displayName = "Meter CSV File",
            description = "Contains meter configuration and metadata",
            fileName = "meter.csv"
        ),
        RequiredFileConfig(
            type = RequiredFile.FileType.PRINTER,
            displayName = "Printer CSV File",
            description = "Contains printer settings and configurations",
            fileName = "printer.csv"
        ),
        RequiredFileConfig(
            type = RequiredFile.FileType.RATE,
            displayName = "Rate CSV File",
            description = "Contains rate tables and calculation parameters",
            fileName = "rate.csv"
        )
    )
)

/**
 * Configuration for a required file type
 */
data class RequiredFileConfig(
    val type: RequiredFile.FileType,
    val displayName: String,
    val description: String,
    val fileName: String,
    val isOptional: Boolean = false,
    val maxSizeMB: Int? = null,
    val validationRules: List<String> = emptyList()
)