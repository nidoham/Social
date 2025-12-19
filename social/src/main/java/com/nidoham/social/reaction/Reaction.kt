package com.nidoham.social.reaction

import androidx.room.TypeConverter
import com.google.gson.Gson

/**
 * Represents reaction counts for a post or story.
 * This is used as an embedded object in Room and a nested object in Firestore.
 *
 * @property likes Count of like reactions
 * @property loves Count of love reactions
 * @property hooray Count of hooray reactions
 * @property wows Count of wow reactions
 * @property kisses Count of kiss reactions
 * @property emojis Count of emoji reactions
 * @property angry Count of angry reactions
 * @property sad Count of sad reactions
 * @property heart Count of heart reactions
 * @property laugh Count of laugh reactions
 * @property confused Count of confused reactions
 * @property eyes Count of eyes reactions
 * @property heartEyes Count of heart eyes reactions
 * @property fire Count of fire reactions
 */
data class Reaction(
    val likes: Int = 0,
    val loves: Int = 0,
    val hooray: Int = 0,
    val wows: Int = 0,
    val kisses: Int = 0,
    val emojis: Int = 0,
    val angry: Int = 0,
    val sad: Int = 0,
    val heart: Int = 0,
    val laugh: Int = 0,
    val confused: Int = 0,
    val eyes: Int = 0,
    val heartEyes: Int = 0,
    val fire: Int = 0
) {
    /**
     * Reaction types available in the application
     */
    enum class Type(val emoji: String, val displayName: String) {
        LIKE("👍", "Like"),
        LOVE("❤️", "Love"),
        HOORAY("🎉", "Hooray"),
        WOW("😮", "Wow"),
        KISS("😘", "Kiss"),
        EMOJI("😊", "Emoji"),
        ANGRY("😠", "Angry"),
        SAD("😢", "Sad"),
        HEART("💖", "Heart"),
        LAUGH("😂", "Laugh"),
        CONFUSED("😕", "Confused"),
        EYES("👀", "Eyes"),
        HEART_EYES("😍", "Heart Eyes"),
        FIRE("🔥", "Fire");

        companion object {
            fun fromString(value: String): Type? {
                return entries.find { it.name.equals(value, ignoreCase = true) }
            }
        }
    }

    /**
     * Gets the count for a specific reaction type
     */
    fun getCount(type: Type): Int {
        return when (type) {
            Type.LIKE -> likes
            Type.LOVE -> loves
            Type.HOORAY -> hooray
            Type.WOW -> wows
            Type.KISS -> kisses
            Type.EMOJI -> emojis
            Type.ANGRY -> angry
            Type.SAD -> sad
            Type.HEART -> heart
            Type.LAUGH -> laugh
            Type.CONFUSED -> confused
            Type.EYES -> eyes
            Type.HEART_EYES -> heartEyes
            Type.FIRE -> fire
        }
    }

    /**
     * Increments a specific reaction type
     */
    fun increment(type: Type): Reaction {
        return when (type) {
            Type.LIKE -> copy(likes = likes + 1)
            Type.LOVE -> copy(loves = loves + 1)
            Type.HOORAY -> copy(hooray = hooray + 1)
            Type.WOW -> copy(wows = wows + 1)
            Type.KISS -> copy(kisses = kisses + 1)
            Type.EMOJI -> copy(emojis = emojis + 1)
            Type.ANGRY -> copy(angry = angry + 1)
            Type.SAD -> copy(sad = sad + 1)
            Type.HEART -> copy(heart = heart + 1)
            Type.LAUGH -> copy(laugh = laugh + 1)
            Type.CONFUSED -> copy(confused = confused + 1)
            Type.EYES -> copy(eyes = eyes + 1)
            Type.HEART_EYES -> copy(heartEyes = heartEyes + 1)
            Type.FIRE -> copy(fire = fire + 1)
        }
    }

    /**
     * Decrements a specific reaction type
     */
    fun decrement(type: Type): Reaction {
        return when (type) {
            Type.LIKE -> copy(likes = maxOf(0, likes - 1))
            Type.LOVE -> copy(loves = maxOf(0, loves - 1))
            Type.HOORAY -> copy(hooray = maxOf(0, hooray - 1))
            Type.WOW -> copy(wows = maxOf(0, wows - 1))
            Type.KISS -> copy(kisses = maxOf(0, kisses - 1))
            Type.EMOJI -> copy(emojis = maxOf(0, emojis - 1))
            Type.ANGRY -> copy(angry = maxOf(0, angry - 1))
            Type.SAD -> copy(sad = maxOf(0, sad - 1))
            Type.HEART -> copy(heart = maxOf(0, heart - 1))
            Type.LAUGH -> copy(laugh = maxOf(0, laugh - 1))
            Type.CONFUSED -> copy(confused = maxOf(0, confused - 1))
            Type.EYES -> copy(eyes = maxOf(0, eyes - 1))
            Type.HEART_EYES -> copy(heartEyes = maxOf(0, heartEyes - 1))
            Type.FIRE -> copy(fire = maxOf(0, fire - 1))
        }
    }

    /**
     * Gets the total count of all reactions
     */
    fun getTotalCount(): Int {
        return likes + loves + hooray + wows + kisses + emojis +
                angry + sad + heart + laugh + confused + eyes + heartEyes + fire
    }

    /**
     * Gets the top reaction type (most used)
     */
    fun getTopReaction(): Type? {
        val counts = Type.entries.associateWith { getCount(it) }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    }

    /**
     * Gets all reaction types with their counts, sorted by count descending
     */
    fun getSortedReactions(): List<Pair<Type, Int>> {
        return Type.entries
            .map { it to getCount(it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    /**
     * Converts to a map for Firestore
     */
    fun toMap(): Map<String, Int> {
        return mapOf(
            "likes" to likes,
            "loves" to loves,
            "hooray" to hooray,
            "wows" to wows,
            "kisses" to kisses,
            "emojis" to emojis,
            "angry" to angry,
            "sad" to sad,
            "heart" to heart,
            "laugh" to laugh,
            "confused" to confused,
            "eyes" to eyes,
            "heartEyes" to heartEyes,
            "fire" to fire
        )
    }

    companion object {
        /**
         * Creates a Reaction from a Firestore map
         */
        fun fromMap(map: Map<String, Any?>): Reaction {
            return Reaction(
                likes = (map["likes"] as? Number)?.toInt() ?: 0,
                loves = (map["loves"] as? Number)?.toInt() ?: 0,
                hooray = (map["hooray"] as? Number)?.toInt() ?: 0,
                wows = (map["wows"] as? Number)?.toInt() ?: 0,
                kisses = (map["kisses"] as? Number)?.toInt() ?: 0,
                emojis = (map["emojis"] as? Number)?.toInt() ?: 0,
                angry = (map["angry"] as? Number)?.toInt() ?: 0,
                sad = (map["sad"] as? Number)?.toInt() ?: 0,
                heart = (map["heart"] as? Number)?.toInt() ?: 0,
                laugh = (map["laugh"] as? Number)?.toInt() ?: 0,
                confused = (map["confused"] as? Number)?.toInt() ?: 0,
                eyes = (map["eyes"] as? Number)?.toInt() ?: 0,
                heartEyes = (map["heartEyes"] as? Number)?.toInt() ?: 0,
                fire = (map["fire"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

/**
 * Room TypeConverter for Reaction objects
 */
class ReactionConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromReaction(reaction: Reaction): String {
        return gson.toJson(reaction)
    }

    @TypeConverter
    fun toReaction(json: String): Reaction {
        return gson.fromJson(json, Reaction::class.java)
    }
}

/**
 * Extension functions for Reaction
 */

/**
 * Checks if there are any reactions
 */
fun Reaction.hasReactions(): Boolean {
    return getTotalCount() > 0
}

/**
 * Gets a summary string of reactions (e.g., "❤️ 5 · 😂 3 · 🔥 2")
 */
fun Reaction.getSummary(maxItems: Int = 3): String {
    return getSortedReactions()
        .take(maxItems)
        .joinToString(" · ") { (type, count) ->
            "${type.emoji} $count"
        }
}

/**
 * Checks if a specific reaction type exists
 */
fun Reaction.hasReaction(type: Reaction.Type): Boolean {
    return getCount(type) > 0
}