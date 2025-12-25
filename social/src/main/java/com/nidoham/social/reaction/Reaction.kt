package com.nidoham.social.reaction

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents aggregate reaction counts for Posts, Comments, or Stories.
 * Optimized with Kotlinx Serialization.
 */
@Serializable
@Parcelize
data class Reaction(
    @SerialName("likes") val likes: Int = 0,
    @SerialName("dislikes") val dislikes: Int = 0, // Facebook এ ডিসলাইক নেই, তবে আপনার প্রজেক্টে থাকলে ঠিক আছে
    @SerialName("loves") val loves: Int = 0,
    @SerialName("wows") val wows: Int = 0,
    @SerialName("angry") val angry: Int = 0,
    @SerialName("sad") val sad: Int = 0,
    @SerialName("laugh") val laugh: Int = 0,
    @SerialName("fire") val fire: Int = 0
) : Parcelable {

    // মোট রিঅ্যাকশন সংখ্যা (Computed Property)
    val total: Int
        get() = likes + dislikes + loves + wows + angry + sad + laugh + fire

    val hasReactions: Boolean
        get() = total > 0

    // নির্দিষ্ট টাইপের কাউন্ট পাওয়ার জন্য হেল্পার
    fun getCount(type: ReactionType): Int {
        return when (type) {
            ReactionType.LIKE -> likes
            ReactionType.DISLIKE -> dislikes
            ReactionType.LOVE -> loves
            ReactionType.WOW -> wows
            ReactionType.ANGRY -> angry
            ReactionType.SAD -> sad
            ReactionType.LAUGH -> laugh
            ReactionType.FIRE -> fire
        }
    }
}

// ================== ENUM ==================

enum class ReactionType(val key: String, val emoji: String) {
    LIKE("likes", "👍"),
    DISLIKE("dislikes", "👎"),
    LOVE("loves", "❤️"),
    WOW("wows", "😮"),
    ANGRY("angry", "😠"),
    SAD("sad", "😢"),
    LAUGH("laugh", "😂"),
    FIRE("fire", "🔥");

    companion object {
        fun fromKey(key: String): ReactionType {
            return entries.find { it.key == key } ?: LIKE
        }
    }
}