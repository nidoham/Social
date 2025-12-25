package com.nidoham.social.posts

import com.nidoham.social.user.User

data class PostWithAuthor(
    val post: Post,
    val author: User
)
