package com.example.meterkenshin.ui.manager

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

data class ExportResult(
    val success: Boolean,
    val exportedCount: Int = 0,
    val errorMessage: String? = null
)
data class FileGroup(
    val name: String,
    val files: List<File>
)

class ExportManager(private val context: Context) {
    private val TAG = "ExportManager"

    /**
     * Get all available files from the app's files directory
     */
    fun getAvailableFiles(): List<File> {
        return try {
            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
            val files = filesDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in listOf("csv", "txt")
            } ?: emptyList()

            Log.d(TAG, "Found ${files.size} files in ${filesDir.absolutePath}")
            files.sortedByDescending { it.lastModified() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available files", e)
            emptyList()
        }
    }

    fun getGroupedFiles(): List<FileGroup> {
        val allFiles = getAvailableFiles()

        val lpFiles = mutableListOf<File>()
        val evFiles = mutableListOf<File>()
        val bdFiles = mutableListOf<File>()
        val otherFiles = mutableListOf<File>()

        allFiles.forEach { file ->
            val fileName = file.name.uppercase()
            when {
                fileName.contains("LP") -> lpFiles.add(file)
                fileName.contains("EV") -> evFiles.add(file)
                fileName.contains("BD") -> bdFiles.add(file)
                else -> otherFiles.add(file)
            }
        }

        return buildList {
            if (lpFiles.isNotEmpty()) add(FileGroup("Load Profile", lpFiles))
            if (evFiles.isNotEmpty()) add(FileGroup("Event", evFiles))
            if (bdFiles.isNotEmpty()) add(FileGroup("Billing Data", bdFiles))
            if (otherFiles.isNotEmpty()) add(FileGroup("Other", otherFiles))
        }
    }

    /**
     * Export files to the download directory
     */
    fun exportFiles(files: List<File>): ExportResult {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appExportDir = File(downloadDir, "kenshinApp")

            if (!appExportDir.exists() && !appExportDir.mkdirs()) {
                return ExportResult(false, 0, "Failed to create export directory")
            }

            var exportedCount = 0
            val errors = mutableListOf<String>()

            for (file in files) {
                try {
                    val destinationFile = File(appExportDir, file.name)
                    file.copyTo(destinationFile, overwrite = true)
                    exportedCount++
                    Log.d(TAG, "Exported: ${file.name}")
                } catch (e: Exception) {
                    errors.add("Failed to export ${file.name}: ${e.message}")
                    Log.e(TAG, "Error exporting ${file.name}", e)
                }
            }

            if (errors.isEmpty()) {
                ExportResult(true, exportedCount)
            } else {
                ExportResult(false, exportedCount, errors.joinToString("; "))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during export", e)
            ExportResult(false, 0, e.message)
        }
    }

    /**
     * Delete a single file by name
     */
    fun deleteFile(fileName: String): ExportResult {
        return try {
            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(filesDir, fileName)

            if (!file.exists()) {
                return ExportResult(false, 0, "File not found")
            }

            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Deleted file: $fileName")
                ExportResult(true, 1)
            } else {
                ExportResult(false, 0, "Failed to delete file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $fileName", e)
            ExportResult(false, 0, e.message)
        }
    }

    /**
     * Delete multiple files by name
     */
    fun deleteFiles(fileNames: List<String>): ExportResult {
        return try {
            val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
            var deletedCount = 0
            val errors = mutableListOf<String>()

            for (fileName in fileNames) {
                try {
                    val file = File(filesDir, fileName)
                    if (file.exists() && file.delete()) {
                        deletedCount++
                        Log.d(TAG, "Deleted file: $fileName")
                    } else {
                        errors.add("Failed to delete $fileName")
                    }
                } catch (e: Exception) {
                    errors.add("Error deleting $fileName: ${e.message}")
                    Log.e(TAG, "Error deleting file: $fileName", e)
                }
            }

            if (errors.isEmpty()) {
                ExportResult(true, deletedCount)
            } else {
                ExportResult(false, deletedCount, errors.joinToString("; "))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during batch delete", e)
            ExportResult(false, 0, e.message)
        }
    }
}