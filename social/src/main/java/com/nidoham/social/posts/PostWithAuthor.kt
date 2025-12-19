package com.nidoham.social.posts

import com.nidoham.social.user.User

/**
 * Data class combining a Post with its author information
 */
data class PostWithAuthor(
    val post: Post,
    val author: User
)