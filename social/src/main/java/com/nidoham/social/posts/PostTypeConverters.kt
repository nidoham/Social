package com.nidoham.social.posts

import androidx.room.TypeConverter
import com.nidoham.social.reaction.Reaction

/**
 * Type converters for Room database
 */
class PostTypeConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun fromReaction(reaction: Reaction): String {
        return "${reaction.likes},${reaction.loves},${reaction.wows}"
    }

    @TypeConverter
    fun toReaction(value: String): Reaction {
        val parts = value.split(",").map { it.toIntOrNull() ?: 0 }
        return Reaction(
            likes = parts.getOrElse(0) { 0 },
            loves = parts.getOrElse(1) { 0 },
            wows = parts.getOrElse(2) { 0 }
        )
    }
}