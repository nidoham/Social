package com.nidoham.social.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Social media post entity optimized for Firebase Firestore.
 * Reaction counts stored as Map<ReactionType, Long> for efficiency.
 */
data class Post(
    @PropertyName("id") val id: String = "",
    @PropertyName("authorId") val authorId: String = "",
    @PropertyName("content") val content: String = "",
    @PropertyName("contentType") val contentType: PostType = PostType.TEXT,
    @PropertyName("mediaUrls") val mediaUrls: List<String> = emptyList(),

    @PropertyName("reactionCounts")
    val reactionCounts: Map<ReactionType, Long> = emptyMap(),

    @PropertyName("visibility") val visibility: PostVisibility = PostVisibility.PUBLIC,
    @PropertyName("location") val location: String? = null,
    @PropertyName("commentCount") val commentCount: Long = 0L,
    @PropertyName("shareCount") val shareCount: Long = 0L,
    @PropertyName("isEdited") val isEdited: Boolean = false,
    @PropertyName("parentPostId") val parentPostId: String? = null,
    @PropertyName("parentPageId") val parentPageId: String? = null,
    @PropertyName("parentGroupId") val parentGroupId: String? = null,

    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: com.google.firebase.Timestamp? = null,

    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: com.google.firebase.Timestamp? = null,

    @PropertyName("deletedAt") val deletedAt: com.google.firebase.Timestamp? = null,

    @PropertyName("status") val status: PostStatus = PostStatus.ACTIVE,
    @PropertyName("engagementScore") val engagementScore: Long = 0L,
    @PropertyName("hashtags") val hashtags: List<String> = emptyList(),
    @PropertyName("mentions") val mentions: List<String> = emptyList(),
    @PropertyName("embeddedLinks") val embeddedLinks: List<String> = emptyList(),
    @PropertyName("language") val language: String? = null,
    @PropertyName("isSponsored") val isSponsored: Boolean = false
) {

    /**
     * No-arg constructor for Firestore deserialization
     */
    constructor() : this(
        id = "",
        authorId = "",
        content = "",
        contentType = PostType.TEXT,
        mediaUrls = emptyList(),
        reactionCounts = emptyMap(),
        visibility = PostVisibility.PUBLIC,
        location = null,
        commentCount = 0L,
        shareCount = 0L,
        isEdited = false,
        parentPostId = null,
        parentPageId = null,
        parentGroupId = null,
        createdAt = null,
        updatedAt = null,
        deletedAt = null,
        status = PostStatus.ACTIVE,
        engagementScore = 0L,
        hashtags = emptyList(),
        mentions = emptyList(),
        embeddedLinks = emptyList(),
        language = null,
        isSponsored = false
    )
}
