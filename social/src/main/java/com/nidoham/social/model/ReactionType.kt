package com.nidoham.social.model

/**
 * Story reaction types - extensible enum for different emotional responses.
 * Each reaction has an associated emoji and label for UI display.
 *
 * @property emoji Unicode emoji representation
 * @property label Human-readable label for the reaction
 */
enum class ReactionType(val emoji: String, val label: String) {
    LIKE("👍", "Like"),
    LOVE("❤️", "Love"),
    HAHA("😂", "Haha"),
    WOW("😮", "Wow"),
    SAD("😢", "Sad"),
    ANGRY("😠", "Angry"),
    FIRE("🔥", "Fire"),
    CLAP("👏", "Clap");

    companion object {
        /**
         * Get reaction type from string value
         * @param value String representation of reaction type (case-insensitive)
         * @return Matching ReactionType, or LIKE as default
         */
        fun fromString(value: String?): ReactionType {
            if (value.isNullOrBlank()) return LIKE
            return entries.find {
                it.name.equals(value.trim(), ignoreCase = true)
            } ?: LIKE
        }

        /**
         * Get reaction type from string, returning null if invalid
         * @param value String representation of reaction type
         * @return Matching ReactionType, or null if not found
         */
        fun fromStringOrNull(value: String?): ReactionType? {
            if (value.isNullOrBlank()) return null
            return entries.find {
                it.name.equals(value.trim(), ignoreCase = true)
            }
        }

        /**
         * Get all available reactions as a list
         * @return List of all ReactionType values
         */
        fun getAllReactions(): List<ReactionType> = entries.toList()

        /**
         * Get emoji for a reaction type
         * @param type The reaction type
         * @return Emoji string for the reaction
         */
        fun getEmoji(type: ReactionType): String = type.emoji

        /**
         * Get label for a reaction type
         * @param type The reaction type
         * @return Human-readable label for the reaction
         */
        fun getLabel(type: ReactionType): String = type.label

        /**
         * Check if a string represents a valid reaction type
         * @param value String to validate
         * @return true if the string matches a valid reaction type
         */
        fun isValid(value: String?): Boolean {
            if (value.isNullOrBlank()) return false
            return entries.any { it.name.equals(value.trim(), ignoreCase = true) }
        }
    }
}