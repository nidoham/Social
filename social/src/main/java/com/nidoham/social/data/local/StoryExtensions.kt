package com.nidoham.social.data.local

import com.nidoham.social.stories.Story
import com.nidoham.social.stories.StoryType
import com.nidoham.social.stories.StoryVisibility

/**
 * Checks if the story is currently active (not expired).
 */
fun Story.isActive(): Boolean {
    return System.currentTimeMillis() < expiresAt
}

/**
 * Calculates if a user can view this story.
 */
fun Story.canView(currentUserId: String, isFriend: Boolean = false): Boolean {
    if (authorId == currentUserId) return true // নিজের স্টোরি নিজে দেখবে

    return when (visibility) {
        StoryVisibility.PUBLIC -> true
        StoryVisibility.FRIENDS -> isFriend
        StoryVisibility.PRIVATE -> false
        StoryVisibility.CLOSE_FRIENDS -> allowedViewers.contains(currentUserId)
        else -> {

        }
    } as Boolean
}

/**
 * Helper to check if it's a video story
 */
fun Story.isVideo() = contentType == StoryType.VIDEO
