package com.nidoham.social.model

/**
 * Story visibility options that control who can view a story.
 *
 * @property label Short display name
 * @property description Detailed description of visibility level
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
         * Get visibility from string value
         * @param value String representation of visibility (case-insensitive)
         * @return Matching StoryVisibility, or PUBLIC as default
         */
        fun fromString(value: String?): StoryVisibility {
            if (value.isNullOrBlank()) return PUBLIC
            return entries.find {
                it.name.equals(value.trim(), ignoreCase = true)
            } ?: PUBLIC
        }

        /**
         * Get visibility from string, returning null if invalid
         * @param value String representation of visibility
         * @return Matching StoryVisibility, or null if not found
         */
        fun fromStringOrNull(value: String?): StoryVisibility? {
            if (value.isNullOrBlank()) return null
            return entries.find {
                it.name.equals(value.trim(), ignoreCase = true)
            }
        }

        /**
         * Get all visibility options
         * @return List of all StoryVisibility values
         */
        fun getAllOptions(): List<StoryVisibility> = entries.toList()

        /**
         * Check if a string represents a valid visibility option
         * @param value String to validate
         * @return true if the string matches a valid visibility option
         */
        fun isValid(value: String?): Boolean {
            if (value.isNullOrBlank()) return false
            return entries.any { it.name.equals(value.trim(), ignoreCase = true) }
        }

        /**
         * Get default visibility for new stories
         * @return PUBLIC visibility
         */
        fun getDefault(): StoryVisibility = PUBLIC
    }
}