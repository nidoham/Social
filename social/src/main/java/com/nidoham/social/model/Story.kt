package com.nidoham.social.model

/**
 * Represents a story post in the social media application.
 * Stories are temporary content that expire after a set duration.
 */
data class Story(
    val id: String,
    val authorId: String,
    val caption: String = "",
    val contentType: ContentType = ContentType.IMAGE,
    val mediaUrls: List<String> = emptyList(),
    val stats: StoryStats = StoryStats(),
    val metadata: StoryMetadata = StoryMetadata()
) {
    /**
     * Check if story has any media attachments
     */
    fun hasMedia(): Boolean = mediaUrls.isNotEmpty()

    /**
     * Get the primary media URL (first in list)
     */
    fun getPrimaryMediaUrl(): String? = mediaUrls.firstOrNull()

    /**
     * Check if story has expired
     */
    fun isExpired(): Boolean = System.currentTimeMillis() > metadata.expiresAt

    /**
     * Check if story is active (not expired, not deleted, not banned)
     */
    fun isActive(): Boolean = !isExpired() && !metadata.isDeleted && !metadata.isBanned

    /**
     * Get total reaction count across all reaction types
     */
    fun getTotalReactionCount(): Long = stats.getTotalReactionCount()

    enum class ContentType {
        IMAGE,
        VIDEO,
        TEXT // For text-only stories with background color
    }
}