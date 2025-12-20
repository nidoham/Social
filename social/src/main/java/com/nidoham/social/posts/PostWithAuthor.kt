package com.nidoham.social.posts

import com.nidoham.social.stories.Story
import com.nidoham.social.user.User

/**
 * Data class combining a Post with its author information
 */
data class PostWithAuthor(
    val post: Post,
    val author: User
) {
    /**
     * Quick access to post properties
     */
    val postId: String get() = post.id
    val caption: String? get() = post.content
    val mediaUrls: List<String> get() = post.mediaUrls
    val contentType: Story.ContentType get() = Story.ContentType.fromString(post.contentType)
    val createdAt: Long get() = post.createdAt
    val viewsCount: Int get() = post.viewsCount
    val reactionsCount: Int get() = post.reactions.getTotalCount()

    /**
     * Quick access to author properties
     */
    val authorId: String get() = author.id
    val authorName: String get() = author.name
    val authorUsername: String get() = author.username
    val authorAvatar: String? get() = author.avatarUrl
    val isAuthorVerified: Boolean get() = author.verified
    val isAuthorPremium: Boolean get() = author.premium

    /**
     * Format for display in UI
     */
    fun getDisplayInfo(): DisplayInfo {
        return DisplayInfo(
            postId = postId,
            authorName = authorName,
            authorUsername = "@${authorUsername}",
            authorAvatar = authorAvatar,
            isVerified = isAuthorVerified,
            caption = caption,
            mediaUrl = mediaUrls.firstOrNull(),
            viewsCount = viewsCount,
            reactionsCount = reactionsCount,
            isPremium = isAuthorPremium
        )
    }

    /**
     * Data class for formatted display information
     */
    data class DisplayInfo(
        val postId: String,
        val authorName: String,
        val authorUsername: String,
        val authorAvatar: String?,
        val isVerified: Boolean,
        val caption: String?,
        val mediaUrl: String?,
        val viewsCount: Int,
        val reactionsCount: Int,
        val isPremium: Boolean
    )
}

/**
 * Extension function to group posts by author
 * Useful for displaying posts grouped by user in UI
 */
fun List<PostWithAuthor>.groupByAuthor(): Map<User, List<Post>> {
    return this.groupBy({ it.author }, { it.post })
}

/**
 * Extension function to get unique authors from post list
 */
fun List<PostWithAuthor>.getUniqueAuthors(): List<User> {
    return this.map { it.author }.distinctBy { it.id }
}

/**
 * Extension function to filter posts by author
 */
fun List<PostWithAuthor>.filterByAuthor(authorId: String): List<PostWithAuthor> {
    return this.filter { it.authorId == authorId }
}

/**
 * Extension function to get the first post for each unique author
 */
fun List<PostWithAuthor>.getFirstPostPerAuthor(): List<PostWithAuthor> {
    return this.groupBy { it.authorId }
        .map { (_, posts) -> posts.first() }
}