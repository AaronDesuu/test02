@file:Suppress("PrivatePropertyName")

package com.example.meterkenshin.ui.manager

import android.media.MediaScannerConnection
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.meterkenshin.data.FileStorageManager
import java.io.File

data class ExportResult(
    val success: Boolean,
    val exportedCount: Int = 0,
    val errorMessage: String? = null,
    val exportedFiles: List<File> = emptyList()
)

data class FileGroup(
    val name: String,
    val files: List<File>
)

class ExportManager(
    private val context: Context,
    private val username: String
) {
    private val TAG = "ExportManager"

    fun getAvailableFiles(): List<File> {
        return try {
            val fileStorageManager = FileStorageManager(context)
            val userFilesDir = fileStorageManager.getUserStorageDirectory(username)

            val files = userFilesDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in listOf("csv", "txt")
            } ?: emptyList()

            Log.d(TAG, "Found ${files.size} files for user $username in ${userFilesDir.absolutePath}")
            files.sortedByDescending { it.lastModified() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available files for user $username", e)
            emptyList()
        }
    }

    fun getGroupedFiles(): List<FileGroup> {
        val allFiles = getAvailableFiles()

        val lpFiles = mutableListOf<File>()
        val elFiles = mutableListOf<File>()
        val bdFiles = mutableListOf<File>()
        val otherFiles = mutableListOf<File>()

        allFiles.forEach { file ->
            val fileName = file.name.uppercase()
            when {
                fileName.contains("LP") -> lpFiles.add(file)
                fileName.contains("EL") -> elFiles.add(file)
                fileName.contains("BD") -> bdFiles.add(file)
                else -> otherFiles.add(file)
            }
        }

        return buildList {
            if (lpFiles.isNotEmpty()) add(FileGroup("Load Profile", lpFiles))
            if (elFiles.isNotEmpty()) add(FileGroup("Event Log", elFiles))
            if (bdFiles.isNotEmpty()) add(FileGroup("Billing Data", bdFiles))
            if (otherFiles.isNotEmpty()) add(FileGroup("Other", otherFiles))
        }
    }

    fun exportFiles(files: List<File>): ExportResult {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appExportDir = File(downloadDir, "kenshinApp")

            if (!appExportDir.exists() && !appExportDir.mkdirs()) {
                return ExportResult(false, 0, "Failed to create export directory")
            }

            var exportedCount = 0
            val exportedFiles = mutableListOf<File>()
            val errors = mutableListOf<String>()

            for (file in files) {
                try {
                    val destinationFile = File(appExportDir, file.name)
                    file.copyTo(destinationFile, overwrite = true)
                    exportedCount++
                    exportedFiles.add(destinationFile)
                    Log.d(TAG, "Exported: ${file.name}")
                } catch (e: Exception) {
                    errors.add("Failed to export ${file.name}: ${e.message}")
                    Log.e(TAG, "Error exporting ${file.name}", e)
                }
            }

            if (errors.isEmpty()) {
                // Notify system about new files
                MediaScannerConnection.scanFile(
                    context,
                    exportedFiles.map { it.absolutePath }.toTypedArray(),
                    null,
                    null
                )
                ExportResult(true, exportedCount, exportedFiles = exportedFiles)
            } else {
                // Still notify about successfully exported files
                if (exportedFiles.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        context,
                        exportedFiles.map { it.absolutePath }.toTypedArray(),
                        null,
                        null
                    )
                }
                ExportResult(false, exportedCount, errors.joinToString("; "), exportedFiles)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during export", e)
            ExportResult(false, 0, e.message)
        }
    }

    fun shareFiles(files: List<File>) {
        if (files.isEmpty()) return

        try {
            val uris = files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val shareIntent = Intent().apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share exported files").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing files", e)
        }
    }

    fun deleteFile(fileName: String): ExportResult {
        return try {
            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(filesDir, fileName)

            if (!file.exists()) {
                return ExportResult(false, 0, "File not found")
            }

            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Deleted: $fileName")
                ExportResult(true, 1)
            } else {
                ExportResult(false, 0, "Failed to delete file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            ExportResult(false, 0, e.message)
        }
    }

    fun deleteFiles(fileNames: Set<String>): ExportResult {
        return try {
            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
            var deletedCount = 0

            fileNames.forEach { fileName ->
                val file = File(filesDir, fileName)
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            }

            ExportResult(true, deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting files", e)
            ExportResult(false, 0, e.message)
        }
    }
}