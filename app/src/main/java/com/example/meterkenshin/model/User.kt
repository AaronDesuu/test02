package com.example.meterkenshin.model

import java.util.Date

enum class UserRole(val displayName: String, val dlmsRank: Int) {
    ADMIN("Admin User", 1),    // DLMS RANK_ADMIN
    READER("Reader User", 3)   // DLMS RANK_READER
}

data class User(
    val id: String,
    val username: String,
    val password: String, // In a real app, this should be hashed
    val role: UserRole,
    val fullName: String,
    val email: String,
    val isActive: Boolean = true,
    val createdAt: Date = Date(),
    val lastLoginAt: Date? = null
)

data class UserSession(
    val userId: String,
    val username: String,
    val role: UserRole,
    val loginTime: Date,
    val expiryTime: Date
) {
    fun isValid(): Boolean {
        return Date().before(expiryTime)
    }

    fun getDaysUntilExpiry(): Int {
        val currentTime = Date().time
        val timeUntilExpiry = expiryTime.time - currentTime
        return (timeUntilExpiry / (1000 * 60 * 60 * 24)).toInt()
    }
}



