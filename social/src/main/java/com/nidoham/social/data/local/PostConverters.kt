package com.nidoham.social.data.local

import androidx.room.TypeConverter
import com.nidoham.social.posts.PostStatus
import com.nidoham.social.posts.PostType
import com.nidoham.social.posts.PostVisibility
import kotlinx.serialization.json.Json

class PostConverters {

    private val json = Json { ignoreUnknownKeys = true }

    // ============= List<String> Converter =============
    @TypeConverter
    fun fromList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return runCatching {
            json.decodeFromString<List<String>>(value)
        }.getOrDefault(emptyList())
    }

    // ============= PostType Enum Converter =============
    @TypeConverter
    fun fromPostType(type: PostType): String = type.name
    @TypeConverter
    fun toPostType(value: String): PostType =
        runCatching { PostType.valueOf(value) }.getOrDefault(PostType.TEXT)

    // ============= Visibility Enum Converter =============
    @TypeConverter
    fun fromVisibility(visibility: PostVisibility): String = visibility.name
    @TypeConverter
    fun toVisibility(value: String): PostVisibility =
        runCatching { PostVisibility.valueOf(value) }.getOrDefault(PostVisibility.PUBLIC)

    // ============= Status Enum Converter =============
    @TypeConverter
    fun fromStatus(status: PostStatus): String = status.name
    @TypeConverter
    fun toStatus(value: String): PostStatus =
        runCatching { PostStatus.valueOf(value) }.getOrDefault(PostStatus.ACTIVE)
}