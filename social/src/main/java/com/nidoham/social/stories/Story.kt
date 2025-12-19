package com.nidoham.social.stories

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a story in the social media application.
 * This entity is used for both Room database storage and Firebase Firestore.
 *
 * @property id Unique identifier for the story
 * @property authorId ID of the user who created the story
 * @property caption Optional caption for the story
 * @property contentType Type of content in the story (text, image, video)
 * @property mediaUrls List of URLs to media files associated with the story
 * @property createdAt Timestamp when the story was created
 * @property updatedAt Timestamp when the story was last updated
 * @property expiresAt Timestamp when the story expires (24 hours default)
 * @property isDeleted Indicates if the story has been deleted
 * @property location Optional location associated with the story
 * @property isBanned Indicates if the story is banned
 * @property visibility Visibility setting for the story
 * @property allowedViewers List of user IDs allowed to view the story (for custom visibility)
 * @property duration Duration of the story in seconds
 * @property backgroundColor Optional background color for text stories
 * @property musicUrl Optional URL to music associated with the story
 * @property aspectRatio Aspect ratio of the story media
 * @property reactionsCount Count of reactions on the story
 * @property viewsCount Count of views on the story
 * @property commentsCount Number of comments on the story
 * @property sharesCount Number of shares of the story
 * @property isSponsored Indicates if the story is sponsored
 * @property isPinned Indicates if the story is pinned
 */
@Entity(tableName = "stories")
@IgnoreExtraProperties
@TypeConverters(StoryConverters::class)
data class Story(
    @PrimaryKey
    val id: String = "",

    val authorId: String = "",

    // Optional fields
    val caption: String? = null,
    val location: String? = null,
    val backgroundColor: String? = null,
    val musicUrl: String? = null,

    // Content
    val contentType: String = ContentType.IMAGE.value,
    val mediaUrls: List<String> = emptyList(),
    val aspectRatio: Float = 9f / 16f, // Default vertical story ratio
    val duration: Int = 5, // Default 5 seconds

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24 hours

    // Visibility and access
    val visibility: String = Visibility.PUBLIC.value,
    val allowedViewers: List<String> = emptyList(),

    // Flags
    val isDeleted: Boolean = false,
    val isBanned: Boolean = false,
    val isSponsored: Boolean = false,
    val isPinned: Boolean = false,

    // Engagement metrics
    val reactionsCount: Int = 0,
    val viewsCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0
) {
    /**
     * Content types for stories
     */
    enum class ContentType(val value: String) {
        TEXT("text"),
        IMAGE("image"),
        VIDEO("video");

        companion object {
            fun fromString(value: String): ContentType {
                return entries.find { it.value == value } ?: IMAGE
            }
        }
    }

    /**
     * Visibility settings for stories
     */
    enum class Visibility(val value: String) {
        PUBLIC("public"),
        FRIENDS("friends"),
        CLOSE_FRIENDS("close_friends"),
        CUSTOM("custom"),
        PRIVATE("private");

        companion object {
            fun fromString(value: String): Visibility {
                return entries.find { it.value == value } ?: PUBLIC
            }
        }
    }

    /**
     * Converts the story to a map for Firestore
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "authorId" to authorId,
            "caption" to caption,
            "contentType" to contentType,
            "mediaUrls" to mediaUrls,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "expiresAt" to expiresAt,
            "isDeleted" to isDeleted,
            "location" to location,
            "isBanned" to isBanned,
            "visibility" to visibility,
            "allowedViewers" to allowedViewers,
            "duration" to duration,
            "backgroundColor" to backgroundColor,
            "musicUrl" to musicUrl,
            "aspectRatio" to aspectRatio,
            "reactionsCount" to reactionsCount,
            "viewsCount" to viewsCount,
            "commentsCount" to commentsCount,
            "sharesCount" to sharesCount,
            "isSponsored" to isSponsored,
            "isPinned" to isPinned
        )
    }

    companion object {
        /**
         * Creates a Story from a Firestore map
         */
        fun fromMap(map: Map<String, Any?>): Story {
            return Story(
                id = map["id"] as? String ?: "",
                authorId = map["authorId"] as? String ?: "",
                caption = map["caption"] as? String,
                contentType = map["contentType"] as? String ?: ContentType.IMAGE.value,
                mediaUrls = (map["mediaUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                expiresAt = (map["expiresAt"] as? Number)?.toLong()
                    ?: System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                isDeleted = map["isDeleted"] as? Boolean ?: false,
                location = map["location"] as? String,
                isBanned = map["isBanned"] as? Boolean ?: false,
                visibility = map["visibility"] as? String ?: Visibility.PUBLIC.value,
                allowedViewers = (map["allowedViewers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                duration = (map["duration"] as? Number)?.toInt() ?: 5,
                backgroundColor = map["backgroundColor"] as? String,
                musicUrl = map["musicUrl"] as? String,
                aspectRatio = (map["aspectRatio"] as? Number)?.toFloat() ?: 9f / 16f,
                reactionsCount = (map["reactionsCount"] as? Number)?.toInt() ?: 0,
                viewsCount = (map["viewsCount"] as? Number)?.toInt() ?: 0,
                commentsCount = (map["commentsCount"] as? Number)?.toInt() ?: 0,
                sharesCount = (map["sharesCount"] as? Number)?.toInt() ?: 0,
                isSponsored = map["isSponsored"] as? Boolean ?: false,
                isPinned = map["isPinned"] as? Boolean ?: false
            )
        }
    }
}

/**
 * Type converters for Room database to handle complex types
 */
class StoryConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
}

/**
 * Extension functions for Story
 */

/**
 * Checks if the story has expired
 */
fun Story.isExpired(): Boolean {
    return System.currentTimeMillis() >= expiresAt
}

/**
 * Checks if the story is active (not deleted, not banned, not expired)
 */
fun Story.isActive(): Boolean {
    return !isDeleted && !isBanned && !isExpired()
}

/**
 * Gets time remaining until story expires in milliseconds
 */
fun Story.getTimeRemaining(): Long {
    val remaining = expiresAt - System.currentTimeMillis()
    return if (remaining > 0) remaining else 0L
}

/**
 * Gets time remaining as hours
 */
fun Story.getHoursRemaining(): Int {
    return (getTimeRemaining() / (1000 * 60 * 60)).toInt()
}

/**
 * Checks if user can view this story based on visibility settings
 */
fun Story.canUserView(userId: String, isFriend: Boolean = false, isCloseFriend: Boolean = false): Boolean {
    if (isDeleted || isBanned) return false
    if (isExpired()) return false
    if (authorId == userId) return true // Author can always view

    return when (Story.Visibility.fromString(visibility)) {
        Story.Visibility.PUBLIC -> true
        Story.Visibility.FRIENDS -> isFriend
        Story.Visibility.CLOSE_FRIENDS -> isCloseFriend
        Story.Visibility.CUSTOM -> allowedViewers.contains(userId)
        Story.Visibility.PRIVATE -> false
    }
}

/**
 * Creates a copy with incremented view count
 */
fun Story.incrementViews(): Story {
    return copy(viewsCount = viewsCount + 1)
}

/**
 * Creates a copy with incremented reactions count
 */
fun Story.incrementReactions(): Story {
    return copy(reactionsCount = reactionsCount + 1)
}

/**
 * Creates a copy with incremented shares count
 */
fun Story.incrementShares(): Story {
    return copy(sharesCount = sharesCount + 1)
}