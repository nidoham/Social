package com.nidoham.social.model

/**
 * Contains system-level metadata and story configuration.
 * Handles timestamps, privacy settings, and display options.
 */
data class StoryMetadata(
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + STORY_DURATION_MS,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val location: String? = null,
    val isBanned: Boolean = false,

    // Privacy settings
    val visibility: StoryVisibility = StoryVisibility.PUBLIC,
    val allowedViewers: List<String> = emptyList(), // User IDs for custom visibility

    // Display settings
    val duration: Int = DEFAULT_SLIDE_DURATION, // Duration in seconds per slide
    val backgroundColor: String? = null, // Hex color for text-only stories
    val musicUrl: String? = null, // Background music URL
    val aspectRatio: Float = 9f / 16f // Default portrait aspect ratio
) {
    /**
     * Check if viewer has permission to see this story
     */
    fun canView(viewerId: String, authorId: String): Boolean {
        // Author can always view their own story
        if (viewerId == authorId) return true

        return when (visibility) {
            StoryVisibility.PUBLIC -> true
            StoryVisibility.FRIENDS -> true // Would need friend check in real implementation
            StoryVisibility.CUSTOM -> viewerId in allowedViewers
            StoryVisibility.PRIVATE -> false
        }
    }

    /**
     * Get remaining time until expiration in milliseconds
     */
    fun getRemainingTime(): Long = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)

    /**
     * Get remaining time as formatted string (e.g., "2h", "45m", "10s")
     */
    fun getRemainingTimeFormatted(): String {
        val remaining = getRemainingTime()
        val hours = remaining / (1000 * 60 * 60)
        val minutes = (remaining / (1000 * 60)) % 60
        val seconds = (remaining / 1000) % 60

        return when {
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /**
     * Check if story is still within its lifetime
     */
    fun isValid(): Boolean = System.currentTimeMillis() < expiresAt

    /**
     * Update the updatedAt timestamp
     */
    fun markAsUpdated(): StoryMetadata {
        return copy(updatedAt = System.currentTimeMillis())
    }

    companion object {
        const val STORY_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val DEFAULT_SLIDE_DURATION = 5 // seconds
    }
}