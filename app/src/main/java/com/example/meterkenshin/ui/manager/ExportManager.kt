package com.example.meterkenshin.ui.manager

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ExportResult(
    val success: Boolean,
    val exportedCount: Int = 0,
    val errorMessage: String? = null
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

    /**
     * Export selected files to Download/kenshinApp directory
     */
    fun exportFiles(files: List<File>): ExportResult {
        return try {
            // Get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            // Create kenshinApp subdirectory
            val kenshinAppDir = File(downloadsDir, "kenshinApp")
            if (!kenshinAppDir.exists()) {
                val created = kenshinAppDir.mkdirs()
                if (!created) {
                    return ExportResult(
                        success = false,
                        errorMessage = "Failed to create export directory"
                    )
                }
                Log.d(TAG, "Created directory: ${kenshinAppDir.absolutePath}")
            }

            // Copy files
            var exportedCount = 0
            files.forEach { sourceFile ->
                try {
                    val destinationFile = File(kenshinAppDir, sourceFile.name)
                    copyFile(sourceFile, destinationFile)
                    exportedCount++
                    Log.d(TAG, "Exported: ${sourceFile.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to export ${sourceFile.name}", e)
                    // Continue with other files
                }
            }

            if (exportedCount == 0) {
                ExportResult(
                    success = false,
                    errorMessage = "No files were exported"
                )
            } else {
                ExportResult(
                    success = true,
                    exportedCount = exportedCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export operation failed", e)
            ExportResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Copy file from source to destination
     */
    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}