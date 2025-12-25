package com.nidoham.social.stories

import com.nidoham.social.user.User

data class StoryWithAuthor(
    val story: Story,
    val author: User
)
