package com.nidoham.social.data.local

import androidx.room.TypeConverter
import com.nidoham.social.stories.StoryType
import com.nidoham.social.stories.StoryVisibility
import kotlinx.serialization.json.Json

/**
 * Optimized Converters using Kotlinx Serialization.
 * Much faster than Gson.
 */
class StoryConverters {

    // JSON কনফিগারেশন (লেনিয়েন্ট মোড)
    private val json = Json { ignoreUnknownKeys = true }

    // ============= StoryType Enum Converter =============
    @TypeConverter
    fun fromStoryType(type: StoryType): String = type.name

    @TypeConverter
    fun toStoryType(value: String): StoryType =
        runCatching { StoryType.valueOf(value) }.getOrDefault(StoryType.IMAGE)

    // ============= Visibility Enum Converter =============
    @TypeConverter
    fun fromVisibility(visibility: StoryVisibility): String = visibility.name

    @TypeConverter
    fun toVisibility(value: String): StoryVisibility =
        runCatching { StoryVisibility.valueOf(value) }.getOrDefault(StoryVisibility.PUBLIC)
}