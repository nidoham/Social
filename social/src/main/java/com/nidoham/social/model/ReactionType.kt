package com.nidoham.social.model

/**
 * Story reaction types - extensible enum for different emotional responses.
 * Each reaction has an associated emoji and label for UI display.
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
         * Get reaction type from string, defaults to LIKE if not found
         */
        fun fromString(value: String): ReactionType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: LIKE
        }

        /**
         * Get all available reactions as a list
         */
        fun getAllReactions(): List<ReactionType> = entries

        /**
         * Get emoji for a reaction type
         */
        fun getEmoji(type: ReactionType): String = type.emoji
    }
}