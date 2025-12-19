
package com.nidoham.social.posts

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.nidoham.social.reaction.Reaction

/**
 * Represents a post in the social media application.
 * This entity is used for both Room database storage and Firebase Firestore.
 *
 * @property id Unique identifier for the post
 * @property authorId ID of the user who created the post
 * @property content Content of the post
 * @property contentType Type of content in the post (text, image, video)
 * @property mediaUrls List of URLs to media files associated with the post
 * @property createdAt Timestamp when the post was created
 * @property updatedAt Timestamp when the post was last updated
 * @property visibility Visibility setting for the post
 * @property location Optional location associated with the post
 * @property reactions Reaction object containing reaction counts
 * @property commentsCount Number of comments on the post
 * @property sharesCount Number of shares of the post
 * @property viewsCount Count of views on the post
 * @property isSponsored Indicates if the post is sponsored
 * @property isPinned Indicates if the post is pinned
 * @property isDeleted Indicates if the post has been deleted
 * @property isBanned Indicates if the post is banned
 * @property status Status of the post
 * @property hashtags List of hashtags used in the post
 * @property mentions List of user IDs mentioned in the post
 */
@Entity(tableName = "posts")
@IgnoreExtraProperties
data class Post(
    @PrimaryKey
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val content: String = "",
    val contentType: String = "text",
    val mediaUrls: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val visibility: String = "public",
    val location: String? = null,
    val reactions: Reaction = Reaction(),
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val isSponsored: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val isBanned: Boolean = false,
    val status: String = "active",
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList()
) {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        authorId = "",
        content = "",
        contentType = "text",
        mediaUrls = emptyList(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        visibility = "public",
        location = null,
        reactions = Reaction(),
        commentsCount = 0,
        sharesCount = 0,
        viewsCount = 0,
        isSponsored = false,
        isPinned = false,
        isDeleted = false,
        isBanned = false,
        status = "active",
        hashtags = emptyList(),
        mentions = emptyList()
    )
}