package com.nidoham.social.reaction

/**
 * Represents an individual user's reaction on a post or comment
 * Used for displaying who reacted and with what type
 */
data class UserReaction(
    val userId: String,
    val reactionType: Reaction.Type,
    val timestamp: Long
) {
    /**
     * Convert to Firestore map
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "reactionType" to reactionType.key,
            "timestamp" to timestamp
        )
    }

    companion object {
        /**
         * Create UserReaction from Firestore map
         */
        fun fromMap(userId: String, map: Map<String, Any?>): UserReaction? {
            val reactionTypeKey = map["reactionType"] as? String ?: return null
            val reactionType = Reaction.Type.fromKey(reactionTypeKey) ?: return null
            val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return UserReaction(
                userId = userId,
                reactionType = reactionType,
                timestamp = timestamp
            )
        }
    }
}

/**
 * Extension functions for UserReaction
 */

/**
 * Check if reaction was made recently (within last 5 minutes)
 */
fun UserReaction.isRecent(): Boolean {
    val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
    return timestamp >= fiveMinutesAgo
}

/**
 * Get time elapsed since reaction in seconds
 */
fun UserReaction.getElapsedSeconds(): Long {
    return (System.currentTimeMillis() - timestamp) / 1000
}

/**
 * Format elapsed time for display
 * Example: "2m ago", "1h ago", "3d ago"
 */
fun UserReaction.getFormattedElapsedTime(): String {
    val seconds = getElapsedSeconds()
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}