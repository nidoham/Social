package com.nidoham.social.model

import com.google.firebase.firestore.PropertyName

/**
 * Simple reaction counter for stories and posts.
 * Tracks counts for different reaction types.
 *
 * @property type The type of reaction (like, love, etc.)
 * @property count The number of times this reaction has been used (always >= 0)
 */
data class Reaction(
    @get:PropertyName("type")
    @set:PropertyName("type")
    var type: ReactionType = ReactionType.LIKE,

    @get:PropertyName("count")
    @set:PropertyName("count")
    var count: Long = 0L
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(ReactionType.LIKE, 0L)

    /**
     * Increment reaction count
     * @return New Reaction instance with incremented count
     */
    fun increment(): Reaction = copy(count = count + 1)

    /**
     * Decrement reaction count (minimum 0)
     * @return New Reaction instance with decremented count, minimum 0
     */
    fun decrement(): Reaction = copy(count = maxOf(0L, count - 1))

    /**
     * Check if reaction has any counts
     * @return true if count > 0
     */
    fun hasCount(): Boolean = count > 0

    /**
     * Get formatted count string for UI display
     * @return Formatted string (e.g., "1.2k", "5M", "234")
     */
    fun getFormattedCount(): String {
        return when {
            count < 1000 -> count.toString()
            count < 1_000_000 -> String.format("%.1fk", count / 1000.0)
            else -> String.format("%.1fM", count / 1_000_000.0)
        }
    }

    /**
     * Validate that the reaction data is valid
     * @return true if the reaction is valid
     */
    fun isValid(): Boolean = count >= 0
}

/**
 * Extension functions for working with collections of Reactions
 */

/**
 * Convert list of reactions to map for efficient lookups
 * @return Map of ReactionType to count, excluding invalid entries
 */
fun List<Reaction>?.toReactionMap(): Map<ReactionType, Long> {
    return this?.filter { it.isValid() }
        ?.associate { it.type to it.count }
        ?: emptyMap()
}

/**
 * Get count for a specific reaction type
 * @param type The reaction type to look up
 * @return Count for the specified type, or 0 if not found
 */
fun List<Reaction>?.getCount(type: ReactionType): Long {
    return this?.firstOrNull { it.type == type }?.count ?: 0L
}

/**
 * Get total count across all reactions
 * @return Sum of all reaction counts
 */
fun List<Reaction>?.getTotalCount(): Long {
    return this?.sumOf { it.count } ?: 0L
}

/**
 * Get the most popular reaction
 * @return Reaction with highest count, or null if list is empty
 */
fun List<Reaction>?.getMostPopular(): Reaction? {
    return this?.filter { it.hasCount() }?.maxByOrNull { it.count }
}

/**
 * Filter reactions that have counts > 0
 * @return List of reactions with non-zero counts
 */
fun List<Reaction>?.withCounts(): List<Reaction> {
    return this?.filter { it.hasCount() } ?: emptyList()
}

/**
 * Create a reaction list from a map
 * @return List of Reaction objects, excluding entries with negative counts
 */
fun Map<ReactionType, Long>?.toReactionList(): List<Reaction> {
    return this?.filterValues { it >= 0 }
        ?.map { (type, count) -> Reaction(type, count) }
        ?: emptyList()
}