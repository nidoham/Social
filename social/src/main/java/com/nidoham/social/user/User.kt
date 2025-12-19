package com.nidoham.social.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Represents a user in the social media application.
 * This entity is used for both Room database storage and Firebase Firestore.
 *
 * @property id Unique identifier for the user
 * @property username Username of the user
 * @property name Full name of the user
 * @property email Email address of the user
 * @property avatarUrl URL to the user's avatar image (null if not set)
 * @property coverUrl URL to the user's cover image (null if not set)
 * @property bio Short bio or description of the user (null if not set)
 * @property birthDate Date of birth in milliseconds (null if not set)
 * @property createdAt Timestamp when the user account was created
 * @property onlineAt Timestamp when the user last went online
 * @property verified Indicates if the user's account is verified
 * @property privacy User's privacy setting (public, private, or friends_only)
 * @property banned Indicates if the user is banned
 * @property premium Indicates if the user has a premium account
 * @property role User's role in the application (admin, moderator, or user)
 * @property gender User's gender (male, female, or other)
 * @property location User's location (null if not set)
 * @property posts Number of posts created by the user
 * @property followers Number of users following this user
 * @property following Number of users this user is following
 */

@IgnoreExtraProperties
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String = "",

    val username: String = "",
    val name: String = "",
    val email: String = "",

    // Nullable fields for optional data
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val bio: String? = null,
    val birthDate: Long? = null,
    val location: String? = null,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val onlineAt: Long = System.currentTimeMillis(),

    // Boolean flags
    val verified: Boolean = false,
    val banned: Boolean = false,
    val premium: Boolean = false,

    // Enums as strings
    val privacy: String = Privacy.PUBLIC.value,
    val role: String = Role.USER.value,
    val gender: String = Gender.OTHER.value,

    // Counters
    val posts: Int = 0,
    val followers: Int = 0,
    val following: Int = 0
) {
    /**
     * Privacy settings for user profiles
     */
    enum class Privacy(val value: String) {
        PUBLIC("public"),
        PRIVATE("private"),
        FRIENDS_ONLY("friends_only");

        companion object {
            fun fromString(value: String): Privacy {
                return entries.find { it.value == value } ?: PUBLIC
            }
        }
    }

    /**
     * User roles in the application
     */
    enum class Role(val value: String) {
        ADMIN("admin"),
        MODERATOR("moderator"),
        USER("user");

        companion object {
            fun fromString(value: String): Role {
                return entries.find { it.value == value } ?: USER
            }
        }
    }

    /**
     * Gender options
     */
    enum class Gender(val value: String) {
        MALE("male"),
        FEMALE("female"),
        OTHER("other");

        companion object {
            fun fromString(value: String): Gender {
                return entries.find { it.value == value } ?: OTHER
            }
        }
    }

    /**
     * Converts the user to a map for Firestore
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "username" to username,
            "name" to name,
            "email" to email,
            "avatarUrl" to avatarUrl,
            "coverUrl" to coverUrl,
            "bio" to bio,
            "birthDate" to birthDate,
            "createdAt" to createdAt,
            "onlineAt" to onlineAt,
            "verified" to verified,
            "privacy" to privacy,
            "banned" to banned,
            "premium" to premium,
            "role" to role,
            "gender" to gender,
            "location" to location,
            "posts" to posts,
            "followers" to followers,
            "following" to following
        )
    }

    companion object {
        /**
         * Creates a User from a Firestore map
         */
        fun fromMap(map: Map<String, Any?>): User {
            return User(
                id = map["id"] as? String ?: "",
                username = map["username"] as? String ?: "",
                name = map["name"] as? String ?: "",
                email = map["email"] as? String ?: "",
                avatarUrl = map["avatarUrl"] as? String,
                coverUrl = map["coverUrl"] as? String,
                bio = map["bio"] as? String,
                birthDate = (map["birthDate"] as? Number)?.toLong(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                onlineAt = (map["onlineAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                verified = map["verified"] as? Boolean ?: false,
                privacy = map["privacy"] as? String ?: Privacy.PUBLIC.value,
                banned = map["banned"] as? Boolean ?: false,
                premium = map["premium"] as? Boolean ?: false,
                role = map["role"] as? String ?: Role.USER.value,
                gender = map["gender"] as? String ?: Gender.OTHER.value,
                location = map["location"] as? String,
                posts = (map["posts"] as? Number)?.toInt() ?: 0,
                followers = (map["followers"] as? Number)?.toInt() ?: 0,
                following = (map["following"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

/**
 * Extension functions for User
 */

/**
 * Checks if the user is online (online within the last 5 minutes)
 */
fun User.isOnline(): Boolean {
    val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
    return onlineAt >= fiveMinutesAgo
}

/**
 * Checks if the user is an admin or moderator
 */
fun User.isStaff(): Boolean {
    return role == User.Role.ADMIN.value || role == User.Role.MODERATOR.value
}

/**
 * Gets the user's age from birthDate
 */
fun User.getAge(): Int? {
    birthDate ?: return null
    val birthYear = java.util.Calendar.getInstance().apply {
        timeInMillis = birthDate
    }.get(java.util.Calendar.YEAR)

    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return currentYear - birthYear
}