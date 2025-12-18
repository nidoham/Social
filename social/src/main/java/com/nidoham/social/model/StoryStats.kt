package com.nidoham.social.model

/**
 * Contains statistical data for story engagement.
 * All counts default to 0 for new stories.
 */
data class StoryStats(
    val reactionCounts: Map<ReactionType, Long> = emptyMap(),
    val viewCount: Long = 0L,
    val replyCount: Long = 0L,
    val shareCount: Long = 0L
) {
    /**
     * Get count for a specific reaction type
     */
    fun getReactionCount(type: ReactionType): Long = reactionCounts[type] ?: 0L

    /**
     * Get total count across all reaction types
     */
    fun getTotalReactionCount(): Long = reactionCounts.values.sum()

    /**
     * Check if story has any reactions
     */
    fun hasReactions(): Boolean = reactionCounts.isNotEmpty() && getTotalReactionCount() > 0

    /**
     * Get the most popular reaction type
     */
    fun getMostPopularReaction(): ReactionType? {
        return reactionCounts.maxByOrNull { it.value }?.key
    }

    /**
     * Increment a specific reaction type
     */
    fun incrementReaction(type: ReactionType): StoryStats {
        val currentCount = getReactionCount(type)
        val updatedCounts = reactionCounts.toMutableMap().apply {
            put(type, currentCount + 1)
        }
        return copy(reactionCounts = updatedCounts)
    }

    /**
     * Decrement a specific reaction type
     */
    fun decrementReaction(type: ReactionType): StoryStats {
        val currentCount = getReactionCount(type)
        if (currentCount <= 0) return this

        val updatedCounts = reactionCounts.toMutableMap().apply {
            put(type, currentCount - 1)
        }
        return copy(reactionCounts = updatedCounts)
    }
}