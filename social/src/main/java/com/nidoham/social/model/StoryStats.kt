package com.nidoham.social.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Contains statistical data for story engagement.
 * All counts default to 0 for new stories.
 *
 * @property reactionCounts Map of reaction types to their counts
 * @property viewCount Number of unique views
 * @property replyCount Number of replies to the story
 * @property shareCount Number of times the story was shared
 */
data class StoryStats(
    @get:PropertyName("reactionCounts")
    @set:PropertyName("reactionCounts")
    var reactionCounts: Map<String, Long> = emptyMap(),

    @get:PropertyName("viewCount")
    @set:PropertyName("viewCount")
    var viewCount: Long = 0L,

    @get:PropertyName("replyCount")
    @set:PropertyName("replyCount")
    var replyCount: Long = 0L,

    @get:PropertyName("shareCount")
    @set:PropertyName("shareCount")
    var shareCount: Long = 0L
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(emptyMap(), 0L, 0L, 0L)

    // REMOVED init block to fix Firestore deserialization issue
    // Validation is now done through isValid() method

    /**
     * Get count for a specific reaction type
     * @param type The reaction type
     * @return Count for the specified reaction, or 0 if not found
     */
    @Exclude
    fun getReactionCount(type: ReactionType): Long {
        return reactionCounts[type.name] ?: 0L
    }

    /**
     * Get total count across all reaction types
     * @return Sum of all reaction counts
     */
    @Exclude
    fun getTotalReactionCount(): Long = reactionCounts.values.sum()

    /**
     * Check if story has any reactions
     * @return true if there are any reactions with count > 0
     */
    @Exclude
    fun hasReactions(): Boolean = reactionCounts.isNotEmpty() && getTotalReactionCount() > 0

    /**
     * Get the most popular reaction type
     * @return ReactionType with highest count, or null if no reactions
     */
    @Exclude
    fun getMostPopularReaction(): ReactionType? {
        val maxEntry = reactionCounts
            .filterValues { it > 0 }
            .maxByOrNull { it.value }
            ?: return null

        return ReactionType.fromStringOrNull(maxEntry.key)
    }

    /**
     * Get all reactions as a list of Reaction objects
     * @return List of Reaction objects with counts > 0
     */
    @Exclude
    fun getReactionsList(): List<Reaction> {
        return reactionCounts
            .filterValues { it > 0 }
            .mapNotNull { (typeStr, count) ->
                ReactionType.fromStringOrNull(typeStr)?.let { type ->
                    Reaction(type, count)
                }
            }
    }

    /**
     * Increment a specific reaction type
     * @param type The reaction type to increment
     * @return New StoryStats instance with incremented reaction count
     */
    fun incrementReaction(type: ReactionType): StoryStats {
        val currentCount = getReactionCount(type)
        val updatedCounts = reactionCounts.toMutableMap().apply {
            put(type.name, currentCount + 1)
        }
        return copy(reactionCounts = updatedCounts)
    }

    /**
     * Decrement a specific reaction type (minimum 0)
     * @param type The reaction type to decrement
     * @return New StoryStats instance with decremented reaction count
     */
    fun decrementReaction(type: ReactionType): StoryStats {
        val currentCount = getReactionCount(type)
        if (currentCount <= 0) return this

        val newCount = currentCount - 1
        val updatedCounts = if (newCount == 0L) {
            reactionCounts.toMutableMap().apply { remove(type.name) }
        } else {
            reactionCounts.toMutableMap().apply { put(type.name, newCount) }
        }

        return copy(reactionCounts = updatedCounts)
    }

    /**
     * Set reaction count for a specific type
     * @param type The reaction type
     * @param count The new count (must be >= 0)
     * @return New StoryStats instance with updated count
     */
    fun setReactionCount(type: ReactionType, count: Long): StoryStats {
        require(count >= 0) { "Count must be non-negative" }

        val updatedCounts = if (count == 0L) {
            reactionCounts.toMutableMap().apply { remove(type.name) }
        } else {
            reactionCounts.toMutableMap().apply { put(type.name, count) }
        }

        return copy(reactionCounts = updatedCounts)
    }

    /**
     * Increment view count
     * @return New StoryStats instance with incremented view count
     */
    fun incrementViews(): StoryStats = copy(viewCount = viewCount + 1)

    /**
     * Increment reply count
     * @return New StoryStats instance with incremented reply count
     */
    fun incrementReplies(): StoryStats = copy(replyCount = replyCount + 1)

    /**
     * Increment share count
     * @return New StoryStats instance with incremented share count
     */
    fun incrementShares(): StoryStats = copy(shareCount = shareCount + 1)

    /**
     * Get engagement score (weighted sum of interactions)
     * Formula: reactions * 1 + replies * 2 + shares * 3 + views * 0.1
     * @return Engagement score
     */
    @Exclude
    fun getEngagementScore(): Double {
        return (getTotalReactionCount() * 1.0) +
                (replyCount * 2.0) +
                (shareCount * 3.0) +
                (viewCount * 0.1)
    }

    /**
     * Validate that all stats are valid (non-negative)
     * FIXED: Now does validation without throwing exceptions (safe for Firestore)
     * @return true if all counts are >= 0
     */
    @Exclude
    fun isValid(): Boolean {
        return viewCount >= 0 &&
                replyCount >= 0 &&
                shareCount >= 0 &&
                reactionCounts.values.all { it >= 0 }
    }
}