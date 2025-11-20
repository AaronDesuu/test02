package com.example.meterkenshin.data

import android.content.Context
import java.io.File

/**
 * Manages user-specific file storage
 */
class FileStorageManager(private val context: Context) {

    companion object {
        private const val APP_FILES_FOLDER = "app_files"
    }

    /**
     * Get user-specific storage directory
     * Structure: /storage/emulated/0/Android/data/{package}/files/{username}/app_files/
     */
    fun getUserStorageDirectory(username: String): File {
        val externalFilesDir = context.getExternalFilesDir(null)
        val baseDir = externalFilesDir ?: context.filesDir

        val userDir = File(File(baseDir, username), APP_FILES_FOLDER)
        if (!userDir.exists()) {
            userDir.mkdirs()
        }
        return userDir
    }

    /**
     * Get file from user's storage
     */
    fun getUserFile(username: String, fileName: String): File {
        return File(getUserStorageDirectory(username), fileName)
    }

    /**
     * Check if user's file exists
     */
    fun userFileExists(username: String, fileName: String): Boolean {
        return getUserFile(username, fileName).exists()
    }

    /**
     * List all files for a user
     */
    fun listUserFiles(username: String): List<File> {
        val userDir = getUserStorageDirectory(username)
        return userDir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Delete a user's file
     */
    fun deleteUserFile(username: String, fileName: String): Boolean {
        return getUserFile(username, fileName).delete()
    }

    /**
     * Delete all files for a user
     */
    fun deleteUserDirectory(username: String): Boolean {
        return getUserStorageDirectory(username).deleteRecursively()
    }
}