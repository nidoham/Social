package com.nidoham.social.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Represents a story post in the social media application.
 * Stories are temporary content that expire after a set duration (typically 24 hours).
 *
 * @property id Firestore document ID
 * @property authorId User ID of the story creator
 * @property caption Optional text caption for the story
 * @property contentType Type of content (IMAGE, VIDEO, TEXT)
 * @property mediaUrls List of media URLs (images/videos)
 * @property stats Engagement statistics
 * @property metadata System metadata and configuration
 */
data class Story(
    @DocumentId
    var id: String? = null,

    @get:PropertyName("authorId")
    @set:PropertyName("authorId")
    var authorId: String = "",

    @get:PropertyName("caption")
    @set:PropertyName("caption")
    var caption: String = "",

    @get:PropertyName("contentType")
    @set:PropertyName("contentType")
    var contentType: String = ContentType.IMAGE.name,

    @get:PropertyName("mediaUrls")
    @set:PropertyName("mediaUrls")
    var mediaUrls: List<String> = emptyList(),

    @get:PropertyName("stats")
    @set:PropertyName("stats")
    var stats: StoryStats = StoryStats(),

    @get:PropertyName("metadata")
    @set:PropertyName("metadata")
    var metadata: StoryMetadata = StoryMetadata()
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(
        id = null,
        authorId = "",
        caption = "",
        contentType = ContentType.IMAGE.name,
        mediaUrls = emptyList(),
        stats = StoryStats(),
        metadata = StoryMetadata()
    )

    /**
     * Get content type as enum
     * @return ContentType enum value
     */
    @Exclude
    fun getContentTypeEnum(): ContentType = ContentType.fromString(contentType)

    /**
     * Set content type from enum
     * @param type The new content type
     */
    @Exclude
    fun setContentTypeEnum(type: ContentType) {
        contentType = type.name
    }

    /**
     * Check if story has any media attachments
     * @return true if mediaUrls list is not empty
     */
    @Exclude
    fun hasMedia(): Boolean = mediaUrls.isNotEmpty()

    /**
     * Get the primary media URL (first in list)
     * @return First media URL, or null if no media
     */
    @Exclude
    fun getPrimaryMediaUrl(): String? = mediaUrls.firstOrNull()

    /**
     * Get all media URLs except the first one
     * @return List of secondary media URLs
     */
    @Exclude
    fun getSecondaryMediaUrls(): List<String> = mediaUrls.drop(1)

    /**
     * Check if story has expired
     * @return true if current time is past expiration
     */
    @Exclude
    fun isExpired(): Boolean = metadata.isExpired()

    /**
     * Check if story is active (not expired, not deleted, not banned)
     * @return true if story can be displayed
     */
    @Exclude
    fun isActive(): Boolean {
        return !isExpired() &&
                !metadata.isDeleted &&
                !metadata.isBanned
    }

    /**
     * Get total reaction count across all reaction types
     * @return Sum of all reaction counts
     */
    @Exclude
    fun getTotalReactionCount(): Long = stats.getTotalReactionCount()

    /**
     * Get engagement score
     * @return Weighted engagement score
     */
    @Exclude
    fun getEngagementScore(): Double = stats.getEngagementScore()

    /**
     * Check if viewer can see this story
     * @param viewerId ID of the user attempting to view
     * @param friendIds Optional set of friend IDs for FRIENDS visibility
     * @return true if viewer has permission
     */
    @Exclude
    fun canBeViewedBy(viewerId: String?, friendIds: Set<String>? = null): Boolean {
        // Basic validation
        if (viewerId.isNullOrBlank() || authorId.isBlank()) return false

        // Must be active to be viewable
        if (!isActive()) return false

        return metadata.canView(viewerId, authorId, friendIds)
    }

    /**
     * Validate that story data is complete and valid
     * @return true if all required fields are valid
     */
    @Exclude
    fun isValid(): Boolean {
        return authorId.isNotBlank() &&
                ContentType.isValid(contentType) &&
                stats.isValid() &&
                metadata.isDataValid() &&
                (hasMedia() || caption.isNotBlank() || getContentTypeEnum() == ContentType.TEXT)
    }

    /**
     * Get story age in milliseconds
     * @return Age in milliseconds, or 0 if createdAt is null
     */
    @Exclude
    fun getAgeInMillis(): Long {
        val created = metadata.createdAt ?: return 0L
        return System.currentTimeMillis() - created
    }

    /**
     * Get remaining lifetime as percentage (0-100)
     * @return Percentage of lifetime remaining
     */
    @Exclude
    fun getRemainingLifetimePercentage(): Int {
        val remaining = metadata.getRemainingTime()
        if (remaining == 0L) return 0

        val total = StoryMetadata.STORY_DURATION_MS
        return ((remaining.toDouble() / total) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Story content types
     */
    enum class ContentType {
        IMAGE,
        VIDEO,
        TEXT; // For text-only stories with background color

        companion object {
            /**
             * Get content type from string
             * @param value String representation
             * @return Matching ContentType, or IMAGE as default
             */
            fun fromString(value: String?): ContentType {
                if (value.isNullOrBlank()) return IMAGE
                return entries.find {
                    it.name.equals(value.trim(), ignoreCase = true)
                } ?: IMAGE
            }

            /**
             * Get content type from string, returning null if invalid
             * @param value String representation
             * @return Matching ContentType, or null if not found
             */
            fun fromStringOrNull(value: String?): ContentType? {
                if (value.isNullOrBlank()) return null
                return entries.find {
                    it.name.equals(value.trim(), ignoreCase = true)
                }
            }

            /**
             * Check if string is a valid content type
             * @param value String to validate
             * @return true if valid
             */
            fun isValid(value: String?): Boolean {
                if (value.isNullOrBlank()) return false
                return entries.any { it.name.equals(value.trim(), ignoreCase = true) }
            }
        }
    }

    companion object {
        /**
         * Create a new story with default values
         * @param authorId The story author's ID
         * @param caption Optional caption
         * @param contentType Type of content
         * @param mediaUrls List of media URLs
         * @return New Story instance
         */
        fun create(
            authorId: String,
            caption: String = "",
            contentType: ContentType = ContentType.IMAGE,
            mediaUrls: List<String> = emptyList()
        ): Story {
            require(authorId.isNotBlank()) { "Author ID cannot be blank" }

            return Story(
                authorId = authorId,
                caption = caption.trim(),
                contentType = contentType.name,
                mediaUrls = mediaUrls.filter { it.isNotBlank() },
                stats = StoryStats(),
                metadata = StoryMetadata.createDefault()
            )
        }
    }
}