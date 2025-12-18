package com.nidoham.social.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Contains system-level metadata and story configuration.
 * Handles timestamps, privacy settings, and display options.
 *
 * @property createdAt Timestamp when story was created (milliseconds)
 * @property updatedAt Timestamp when story was last updated (milliseconds)
 * @property expiresAt Timestamp when story expires (milliseconds)
 * @property isEdited Whether the story has been edited
 * @property isDeleted Whether the story has been soft-deleted
 * @property location Optional location tag
 * @property isBanned Whether the story has been banned by moderation
 * @property visibility Who can view this story
 * @property allowedViewers User IDs for custom visibility (only used when visibility is CUSTOM)
 * @property duration Duration in seconds per slide
 * @property backgroundColor Hex color for text-only stories
 * @property musicUrl Background music URL
 * @property aspectRatio Aspect ratio for display
 */
data class StoryMetadata(
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Long? = null,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Long? = null,

    @get:PropertyName("expiresAt")
    @set:PropertyName("expiresAt")
    var expiresAt: Long? = null,

    @get:PropertyName("isEdited")
    @set:PropertyName("isEdited")
    var isEdited: Boolean = false,

    @get:PropertyName("isDeleted")
    @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,

    @get:PropertyName("location")
    @set:PropertyName("location")
    var location: String? = null,

    @get:PropertyName("isBanned")
    @set:PropertyName("isBanned")
    var isBanned: Boolean = false,

    @get:PropertyName("visibility")
    @set:PropertyName("visibility")
    var visibility: String = StoryVisibility.PUBLIC.name,

    @get:PropertyName("allowedViewers")
    @set:PropertyName("allowedViewers")
    var allowedViewers: List<String> = emptyList(),

    @get:PropertyName("duration")
    @set:PropertyName("duration")
    var duration: Int = DEFAULT_SLIDE_DURATION,

    @get:PropertyName("backgroundColor")
    @set:PropertyName("backgroundColor")
    var backgroundColor: String? = null,

    @get:PropertyName("musicUrl")
    @set:PropertyName("musicUrl")
    var musicUrl: String? = null,

    @get:PropertyName("aspectRatio")
    @set:PropertyName("aspectRatio")
    var aspectRatio: Float = DEFAULT_ASPECT_RATIO
) {
    /**
     * No-arg constructor required by Firestore
     * FIXED: Added all parameters to prevent init block issues
     */
    constructor() : this(
        createdAt = null,
        updatedAt = null,
        expiresAt = null,
        isEdited = false,
        isDeleted = false,
        location = null,
        isBanned = false,
        visibility = StoryVisibility.PUBLIC.name,
        allowedViewers = emptyList(),
        duration = DEFAULT_SLIDE_DURATION,
        backgroundColor = null,
        musicUrl = null,
        aspectRatio = DEFAULT_ASPECT_RATIO
    )

    /**
     * Get visibility as enum
     * @return StoryVisibility enum value
     */
    @Exclude
    fun getVisibilityEnum(): StoryVisibility = StoryVisibility.fromString(visibility)

    /**
     * Set visibility from enum
     * @param newVisibility The new visibility setting
     */
    @Exclude
    fun setVisibilityEnum(newVisibility: StoryVisibility) {
        visibility = newVisibility.name
    }

    /**
     * Check if viewer has permission to see this story
     * @param viewerId ID of the user attempting to view
     * @param authorId ID of the story author
     * @param friendIds Set of user IDs who are friends with the viewer (optional)
     * @return true if viewer has permission
     */
    @Exclude
    fun canView(
        viewerId: String?,
        authorId: String?,
        friendIds: Set<String>? = null
    ): Boolean {
        // Null safety checks
        if (viewerId.isNullOrBlank() || authorId.isNullOrBlank()) return false

        // Author can always view their own story
        if (viewerId == authorId) return true

        // Banned or deleted stories cannot be viewed (except by author)
        if (isBanned || isDeleted) return false

        // Check expiration
        if (isExpired()) return false

        return when (getVisibilityEnum()) {
            StoryVisibility.PUBLIC -> true
            StoryVisibility.FRIENDS -> friendIds?.contains(authorId) ?: false
            StoryVisibility.CUSTOM -> viewerId in allowedViewers
            StoryVisibility.PRIVATE -> false
        }
    }

    /**
     * Get remaining time until expiration in milliseconds
     * @return Remaining time in milliseconds, or 0 if expired/null
     */
    @Exclude
    fun getRemainingTime(): Long {
        val expiryTime = expiresAt ?: return 0L
        val remaining = expiryTime - System.currentTimeMillis()
        return maxOf(0L, remaining)
    }

    /**
     * Get remaining time as formatted string
     * @return Formatted string (e.g., "2h", "45m", "10s")
     */
    @Exclude
    fun getRemainingTimeFormatted(): String {
        val remaining = getRemainingTime()
        if (remaining == 0L) return "0s"

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
     * Check if story has expired
     * @return true if current time is past expiration
     */
    @Exclude
    fun isExpired(): Boolean {
        val expiryTime = expiresAt ?: return false
        return System.currentTimeMillis() > expiryTime
    }

    /**
     * Check if story is still within its lifetime
     * @return true if not expired
     */
    @Exclude
    fun isValid(): Boolean = !isExpired()

    /**
     * Update the updatedAt timestamp
     * @return New StoryMetadata instance with updated timestamp
     */
    fun markAsUpdated(): StoryMetadata {
        return copy(
            updatedAt = System.currentTimeMillis(),
            isEdited = true
        )
    }

    /**
     * Mark story as deleted
     * @return New StoryMetadata instance marked as deleted
     */
    fun markAsDeleted(): StoryMetadata {
        return copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark story as banned
     * @return New StoryMetadata instance marked as banned
     */
    fun markAsBanned(): StoryMetadata {
        return copy(isBanned = true, updatedAt = System.currentTimeMillis())
    }

    /**
     * Validate backgroundColor is a valid hex color if present
     * @return true if valid or null
     */
    @Exclude
    fun hasValidBackgroundColor(): Boolean {
        val color = backgroundColor ?: return true
        return color.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))
    }

    /**
     * Validate all fields are within acceptable ranges
     * FIXED: Now does validation without throwing exceptions (safe for Firestore)
     * @return true if all validations pass
     */
    @Exclude
    fun isDataValid(): Boolean {
        return duration > 0 &&
                aspectRatio > 0 &&
                hasValidBackgroundColor() &&
                StoryVisibility.isValid(visibility)
    }

    companion object {
        const val STORY_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val DEFAULT_SLIDE_DURATION = 5 // seconds
        const val DEFAULT_ASPECT_RATIO = 9f / 16f // Portrait aspect ratio
        const val MIN_DURATION = 1 // Minimum 1 second
        const val MAX_DURATION = 15 // Maximum 15 seconds

        /**
         * Create default metadata for a new story
         * @return StoryMetadata with default values
         */
        fun createDefault(): StoryMetadata {
            val now = System.currentTimeMillis()
            val expiry = now + STORY_DURATION_MS
            return StoryMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = expiry
            )
        }
    }
}