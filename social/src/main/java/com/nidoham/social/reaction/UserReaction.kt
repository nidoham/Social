package com.nidoham.social.reaction

import java.util.Locale

/**
 * Get top N reaction types for display (e.g., showing icons of top 3 reactions)
 */
fun Reaction.getTopReactions(limit: Int = 3): List<Pair<ReactionType, Int>> {
    return ReactionType.entries
        .map { it to getCount(it) }
        .filter { it.second > 0 } // যাদের কাউন্ট ০ এর বেশি শুধু তাদের নেবে
        .sortedByDescending { it.second } // বড় থেকে ছোট সাজাবে
        .take(limit)
}

/**
 * UI Display String: "1.2K likes, 50 love"
 */
fun Reaction.formatForDisplay(maxItems: Int = 2): String {
    if (total == 0) return "No reactions"

    return getTopReactions(maxItems)
        .joinToString(", ") { (type, count) ->
            "${count.compactFormat()} ${type.key}"
        }
}

/**
 * Compact number formatter (1200 -> 1.2K)
 */
private fun Int.compactFormat(): String {
    return when {
        this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
        this >= 1_000 -> String.format(Locale.US, "%.1fK", this / 1_000.0)
        else -> this.toString()
    }
}