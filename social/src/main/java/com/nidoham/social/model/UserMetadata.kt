package com.nidoham.social.model

/**
 * Contains system-level metadata and account status.
 * Useful for auditing and account management logic.
 */
data class UserMetadata(
    val createdAt: Long,            // Corrected grammar: createAt -> createdAt
    val updatedAt: Long,            // Corrected grammar: updateAt -> updatedAt
    val lastLoginAt: Long,          // Renamed: lastSignIn -> lastLoginAt
    val lastLogoutAt: Long,         // Renamed: lastSignOut -> lastLogoutAt
    val isVerified: Boolean = false // Default to false
)