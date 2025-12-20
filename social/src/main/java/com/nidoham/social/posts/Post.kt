package com.nidoham.social.posts

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.nidoham.social.reaction.Reaction

/**
 * Represents a post in the social media application.
 * This entity is used for both Room database storage and Firebase Firestore.
 * Reactions are stored separately in Firestore subcollection: /posts/{postId}/reactions/
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
    val mediaUrlsString: String = "", // Comma-separated URLs
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val visibility: String = "public",
    val location: String? = null,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val isSponsored: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val isBanned: Boolean = false,
    val status: String = "active",
    val hashtagsString: String = "", // Comma-separated hashtags
    val mentionsString: String = "" // Comma-separated mentions
) {
    // Reactions are not stored in Room, only fetched from Firestore
    @Ignore
    var reactions: Reaction = Reaction()

    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        authorId = "",
        content = "",
        contentType = "text",
        mediaUrlsString = "",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        visibility = "public",
        location = null,
        commentsCount = 0,
        sharesCount = 0,
        viewsCount = 0,
        isSponsored = false,
        isPinned = false,
        isDeleted = false,
        isBanned = false,
        status = "active",
        hashtagsString = "",
        mentionsString = ""
    )

    // Helper properties for Firestore compatibility
    @get:com.google.firebase.firestore.Exclude
    val mediaUrls: List<String>
        get() = if (mediaUrlsString.isBlank()) emptyList()
        else mediaUrlsString.split(",").map { it.trim() }

    @get:com.google.firebase.firestore.Exclude
    val hashtags: List<String>
        get() = if (hashtagsString.isBlank()) emptyList()
        else hashtagsString.split(",").map { it.trim() }

    @get:com.google.firebase.firestore.Exclude
    val mentions: List<String>
        get() = if (mentionsString.isBlank()) emptyList()
        else mentionsString.split(",").map { it.trim() }

    companion object {
        fun create(
            id: String = "",
            authorId: String = "",
            content: String = "",
            contentType: String = "text",
            mediaUrls: List<String> = emptyList(),
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = System.currentTimeMillis(),
            visibility: String = "public",
            location: String? = null,
            reactions: Reaction = Reaction(),
            commentsCount: Int = 0,
            sharesCount: Int = 0,
            viewsCount: Int = 0,
            isSponsored: Boolean = false,
            isPinned: Boolean = false,
            isDeleted: Boolean = false,
            isBanned: Boolean = false,
            status: String = "active",
            hashtags: List<String> = emptyList(),
            mentions: List<String> = emptyList()
        ): Post {
            val post = Post(
                id = id,
                authorId = authorId,
                content = content,
                contentType = contentType,
                mediaUrlsString = mediaUrls.joinToString(","),
                createdAt = createdAt,
                updatedAt = updatedAt,
                visibility = visibility,
                location = location,
                commentsCount = commentsCount,
                sharesCount = sharesCount,
                viewsCount = viewsCount,
                isSponsored = isSponsored,
                isPinned = isPinned,
                isDeleted = isDeleted,
                isBanned = isBanned,
                status = status,
                hashtagsString = hashtags.joinToString(","),
                mentionsString = mentions.joinToString(",")
            )
            post.reactions = reactions
            return post
        }
    }
}