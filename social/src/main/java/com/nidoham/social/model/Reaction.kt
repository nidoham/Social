package com.nidoham.social.model

/**
 * Simple reaction counter for stories and posts.
 * Tracks counts for different reaction types.
 */
data class Reaction(
    val type: ReactionType,
    val count: Long = 0L
) {
    /**
     * Increment reaction count
     */
    fun increment(): Reaction = copy(count = count + 1)

    /**
     * Decrement reaction count (minimum 0)
     */
    fun decrement(): Reaction = copy(count = (count - 1).coerceAtLeast(0L))

    /**
     * Check if reaction has any counts
     */
    fun hasCount(): Boolean = count > 0

    /**
     * Get formatted count string (e.g., "1.2k", "5M")
     */
    fun getFormattedCount(): String {
        return when {
            count < 1000 -> count.toString()
            count < 1000000 -> String.format("%.1fk", count / 1000.0)
            else -> String.format("%.1fM", count / 1000000.0)
        }
    }
}

/**
 * Extension functions for working with collections of Reactions
 */

/**
 * Convert list of reactions to map for efficient lookups
 */
fun List<Reaction>.toMap(): Map<ReactionType, Long> {
    return associate { it.type to it.count }
}

/**
 * Get count for a specific reaction type
 */
fun List<Reaction>.getCount(type: ReactionType): Long {
    return firstOrNull { it.type == type }?.count ?: 0L
}

/**
 * Get total count across all reactions
 */
fun List<Reaction>.getTotalCount(): Long {
    return sumOf { it.count }
}

/**
 * Get the most popular reaction
 */
fun List<Reaction>.getMostPopular(): Reaction? {
    return maxByOrNull { it.count }
}

/**
 * Filter reactions that have counts > 0
 */
fun List<Reaction>.withCounts(): List<Reaction> {
    return filter { it.hasCount() }
}

/**
 * Create a reaction list from a map
 */
fun Map<ReactionType, Long>.toReactionList(): List<Reaction> {
    return map { (type, count) -> Reaction(type, count) }
}