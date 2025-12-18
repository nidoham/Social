package com.nidoham.social.model

/**
 * Story visibility options that control who can view a story.
 */
enum class StoryVisibility(val label: String, val description: String) {
    PUBLIC(
        "Public",
        "Everyone can view this story"
    ),
    FRIENDS(
        "Friends",
        "Only your friends can view this story"
    ),
    CUSTOM(
        "Custom",
        "Only specific people you choose can view this story"
    ),
    PRIVATE(
        "Private",
        "Only you can view this story"
    );

    companion object {
        /**
         * Get visibility from string, defaults to PUBLIC if not found
         */
        fun fromString(value: String): StoryVisibility {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: PUBLIC
        }

        /**
         * Get all visibility options
         */
        fun getAllOptions(): List<StoryVisibility> = entries
    }
}