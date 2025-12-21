package com.nidoham.social.reaction

/**
 * Represents reaction counts for a post, comment, story, etc.
 * Simple counter for each reaction type.
 */
data class Reaction(
    val likes: Int = 0,
    val dislikes: Int = 0,
    val loves: Int = 0,
    val wows: Int = 0,
    val angry: Int = 0,
    val sad: Int = 0,
    val laugh: Int = 0,
    val fire: Int = 0
) {
    /**
     * Enum representing all available reaction types
     */
    enum class Type(val key: String, val emoji: String) {
        LIKE("likes", "👍"),
        DISLIKE("dislikes", "👎"),
        LOVE("loves", "❤️"),
        WOW("wows", "😮"),
        ANGRY("angry", "😠"),
        SAD("sad", "😢"),
        LAUGH("laugh", "😂"),
        FIRE("fire", "🔥");

        companion object {
            fun fromKey(key: String): Type? {
                return entries.find { it.key == key }
            }
        }
    }

    /**
     * Get total count of all reactions
     */
    val total: Int
        get() = likes + dislikes + loves + wows + angry + sad + laugh + fire

    /**
     * Check if any reactions exist
     */
    val hasReactions: Boolean
        get() = total > 0

    /**
     * Get count for a specific reaction type
     */
    fun getCount(type: Type): Int {
        return when (type) {
            Type.LIKE -> likes
            Type.DISLIKE -> dislikes
            Type.LOVE -> loves
            Type.WOW -> wows
            Type.ANGRY -> angry
            Type.SAD -> sad
            Type.LAUGH -> laugh
            Type.FIRE -> fire
        }
    }

    /**
     * Convert to Firestore map
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "likes" to likes,
            "dislikes" to dislikes,
            "loves" to loves,
            "wows" to wows,
            "angry" to angry,
            "sad" to sad,
            "laugh" to laugh,
            "fire" to fire
        )
    }

    /**
     * Get top N reaction types by count
     */
    fun getTopReactions(limit: Int = 3): List<Pair<Type, Int>> {
        return Type.entries
            .map { it to getCount(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
    }

    companion object {
        /**
         * Create Reaction from Firestore map
         */
        fun fromMap(map: Map<String, Any?>): Reaction {
            return Reaction(
                likes = (map["likes"] as? Number)?.toInt() ?: 0,
                dislikes = (map["dislikes"] as? Number)?.toInt() ?: 0,
                loves = (map["loves"] as? Number)?.toInt() ?: 0,
                wows = (map["wows"] as? Number)?.toInt() ?: 0,
                angry = (map["angry"] as? Number)?.toInt() ?: 0,
                sad = (map["sad"] as? Number)?.toInt() ?: 0,
                laugh = (map["laugh"] as? Number)?.toInt() ?: 0,
                fire = (map["fire"] as? Number)?.toInt() ?: 0
            )
        }

        /**
         * Create empty reaction
         */
        fun empty(): Reaction = Reaction()
    }
}

/**
 * Extension functions for Reaction
 */

/**
 * Format reaction counts for display
 * Example: "1.2K likes, 500 loves"
 */
fun Reaction.formatForDisplay(maxItems: Int = 2): String {
    return getTopReactions(maxItems)
        .joinToString(", ") { (type, count) ->
            "${formatCount(count)} ${type.key}"
        }
}

/**
 * Format a count value (e.g., 1200 -> "1.2K")
 */
private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}