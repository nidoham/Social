package com.nidoham.social.model

/**
 * Enum representing post visibility settings for social media posts.
 * Used for Firebase serialization with consistent JSON keys.
 */
enum class PostVisibility {
    PUBLIC, FRIENDS, PRIVATE, CUSTOM
}