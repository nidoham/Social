package com.nidoham.social.model

/**
 * Enum representing different types of social media posts.
 * Used for Firebase serialization with consistent JSON keys.
 */
enum class PostType {
    TEXT, IMAGE, VIDEO, LINK, POLL, EVENT
}