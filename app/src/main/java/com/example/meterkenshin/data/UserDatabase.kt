package com.example.meterkenshin.data

import com.example.meterkenshin.model.User
import com.example.meterkenshin.model.UserRole
import com.example.meterkenshin.model.UserSession
import java.util.Calendar
import java.util.Date
import java.util.UUID

class UserDatabase private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getInstance(): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserDatabase().also { INSTANCE = it }
            }
        }
    }

    private val users = mutableMapOf<String, User>()
    private var currentSession: UserSession? = null

    init {
        // Initialize with default users
        createDefaultUsers()
    }

    private fun createDefaultUsers() {
        val rootUser = User(
            id = UUID.randomUUID().toString(),
            username = "root",
            password = "root123", // In production, this should be hashed
            role = UserRole.ROOT,
            fullName = "Root Administrator",
            email = "root@meterkenshin.com"
        )

        val adminUser = User(
            id = UUID.randomUUID().toString(),
            username = "admin",
            password = "admin123",
            role = UserRole.ADMIN,
            fullName = "System Administrator",
            email = "admin@meterkenshin.com"
        )

        val readerUser = User(
            id = UUID.randomUUID().toString(),
            username = "reader",
            password = "reader123",
            role = UserRole.READER,
            fullName = "Report Reader",
            email = "reader@meterkenshin.com"
        )

        users[rootUser.username] = rootUser
        users[adminUser.username] = adminUser
        users[readerUser.username] = readerUser
    }

    fun authenticateUser(username: String, password: String): User? {
        val user = users[username]
        return if (user != null && user.password == password && user.isActive) {
            // Update last login time
            val updatedUser = user.copy(lastLoginAt = Date())
            users[username] = updatedUser
            updatedUser
        } else {
            null
        }
    }

    fun createSession(user: User): UserSession {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 7) // 7 days session

        val session = UserSession(
            userId = user.id,
            username = user.username,
            role = user.role,
            loginTime = Date(),
            expiryTime = calendar.time
        )

        currentSession = session
        return session
    }

    fun getCurrentSession(): UserSession? {
        return currentSession?.takeIf { it.isValid() }
    }

    fun invalidateSession() {
        currentSession = null
    }

    fun getAllUsers(): List<User> {
        return users.values.toList()
    }

    fun getUserById(id: String): User? {
        return users.values.find { it.id == id }
    }

    fun getUserByUsername(username: String): User? {
        return users[username]
    }

    fun addUser(user: User): Boolean {
        return if (users.containsKey(user.username)) {
            false // User already exists
        } else {
            users[user.username] = user
            true
        }
    }

    fun updateUser(user: User): Boolean {
        return if (users.containsKey(user.username)) {
            users[user.username] = user
            true
        } else {
            false
        }
    }

    fun deleteUser(username: String): Boolean {
        return users.remove(username) != null
    }

    fun isSessionValid(): Boolean {
        return getCurrentSession() != null
    }
}