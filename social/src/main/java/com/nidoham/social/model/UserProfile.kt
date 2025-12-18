package com.nidoham.social.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Contains the public-facing profile information of a user.
 *
 * @property username Unique handle (e.g., @nidoham) - must be unique across platform
 * @property displayName Full display name shown to other users
 * @property email User's email address
 * @property avatarUrl URL to user's profile picture
 * @property coverUrl Optional URL to user's cover/banner image
 * @property bio Optional biography/description (max 160 characters recommended)
 * @property website Optional website or social media link
 * @property gender Optional gender identity
 * @property pronouns Optional preferred pronouns (e.g., "they/them", "she/her")
 * @property birthDate Date of birth (stored as timestamp in milliseconds)
 * @property location Optional location/city
 * @property phoneNumber Optional verified phone number
 */
data class UserProfile(
    @get:PropertyName("username")
    @set:PropertyName("username")
    var username: String = "",

    @get:PropertyName("displayName")
    @set:PropertyName("displayName")
    var displayName: String = "",

    @get:PropertyName("email")
    @set:PropertyName("email")
    var email: String = "",

    @get:PropertyName("avatarUrl")
    @set:PropertyName("avatarUrl")
    var avatarUrl: String = "",

    @get:PropertyName("coverUrl")
    @set:PropertyName("coverUrl")
    var coverUrl: String? = null,

    @get:PropertyName("bio")
    @set:PropertyName("bio")
    var bio: String? = null,

    @get:PropertyName("website")
    @set:PropertyName("website")
    var website: String? = null,

    @get:PropertyName("gender")
    @set:PropertyName("gender")
    var gender: String? = null,

    @get:PropertyName("pronouns")
    @set:PropertyName("pronouns")
    var pronouns: String? = null,

    @get:PropertyName("birthDate")
    @set:PropertyName("birthDate")
    var birthDate: Long? = null,

    @get:PropertyName("location")
    @set:PropertyName("location")
    var location: String? = null,

    @get:PropertyName("phoneNumber")
    @set:PropertyName("phoneNumber")
    var phoneNumber: String? = null
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(
        username = "",
        displayName = "",
        email = "",
        avatarUrl = "",
        birthDate = null
    )

    /**
     * Get formatted username with @ prefix
     * @return Username prefixed with @
     */
    @Exclude
    fun getFormattedUsername(): String = if (username.isNotBlank()) "@$username" else ""

    /**
     * Get user's age based on birth date
     * @return Age in years, or null if birthDate is not set
     */
    @Exclude
    fun getAge(): Int? {
        val birth = birthDate ?: return null
        val now = System.currentTimeMillis()
        val ageInMillis = now - birth
        return (ageInMillis / (365.25 * 24 * 60 * 60 * 1000)).toInt()
    }

    /**
     * Check if profile has a cover image
     * @return true if coverUrl is not null and not blank
     */
    @Exclude
    fun hasCoverImage(): Boolean = !coverUrl.isNullOrBlank()

    /**
     * Check if profile has a bio
     * @return true if bio is not null and not blank
     */
    @Exclude
    fun hasBio(): Boolean = !bio.isNullOrBlank()

    /**
     * Check if profile has a website
     * @return true if website is not null and not blank
     */
    @Exclude
    fun hasWebsite(): Boolean = !website.isNullOrBlank()

    /**
     * Check if bio exceeds recommended length
     * @return true if bio is longer than MAX_BIO_LENGTH
     */
    @Exclude
    fun isBioTooLong(): Boolean = (bio?.length ?: 0) > MAX_BIO_LENGTH

    /**
     * Get truncated bio for preview
     * @param maxLength Maximum length for truncation
     * @return Truncated bio with ellipsis if needed
     */
    @Exclude
    fun getTruncatedBio(maxLength: Int = 100): String? {
        val bioText = bio ?: return null
        return if (bioText.length > maxLength) {
            "${bioText.take(maxLength)}..."
        } else {
            bioText
        }
    }

    /**
     * Check if user is of legal age (18+)
     * @return true if user is 18 or older, null if birthDate not set
     */
    @Exclude
    fun isLegalAge(): Boolean? {
        val age = getAge() ?: return null
        return age >= LEGAL_AGE
    }

    /**
     * Validate all required fields are present and valid
     * @return true if all required fields are valid
     */
    @Exclude
    fun isValid(): Boolean {
        return username.isNotBlank() && isValidUsername(username) &&
                displayName.isNotBlank() &&
                email.isNotBlank() && isValidEmail(email) &&
                avatarUrl.isNotBlank() &&
                birthDate != null
    }

    /**
     * Check if profile is complete (all recommended fields filled)
     * @return true if profile has all recommended information
     */
    @Exclude
    fun isComplete(): Boolean {
        return isValid() &&
                hasBio() &&
                hasCoverImage() &&
                !location.isNullOrBlank()
    }

    /**
     * Update profile with new data
     * @return New UserProfile instance with updated fields
     */
    fun update(
        newDisplayName: String? = null,
        newBio: String? = null,
        newAvatarUrl: String? = null,
        newCoverUrl: String? = null,
        newWebsite: String? = null,
        newLocation: String? = null,
        newPronouns: String? = null,
        newGender: String? = null
    ): UserProfile {
        return copy(
            displayName = newDisplayName?.takeIf { it.isNotBlank() } ?: displayName,
            bio = newBio?.takeIf { it.isNotBlank() },
            avatarUrl = newAvatarUrl?.takeIf { it.isNotBlank() } ?: avatarUrl,
            coverUrl = newCoverUrl?.takeIf { it.isNotBlank() },
            website = newWebsite?.takeIf { it.isNotBlank() },
            location = newLocation?.takeIf { it.isNotBlank() },
            pronouns = newPronouns?.takeIf { it.isNotBlank() },
            gender = newGender?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        const val MIN_USERNAME_LENGTH = 3
        const val MAX_USERNAME_LENGTH = 30
        const val MAX_BIO_LENGTH = 160
        const val MAX_DISPLAY_NAME_LENGTH = 50
        const val LEGAL_AGE = 18

        /**
         * Validate username format
         * - Length between 3-30 characters
         * - Can contain letters, numbers, underscores, and dots
         * - Cannot start or end with a dot
         * - Cannot have consecutive dots
         */
        fun isValidUsername(username: String): Boolean {
            if (username.length !in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH) return false
            if (username.startsWith('.') || username.endsWith('.')) return false
            if (username.contains("..")) return false
            return username.matches(Regex("^[a-zA-Z0-9_.]+$"))
        }

        /**
         * Validate email format using basic regex
         */
        fun isValidEmail(email: String): Boolean {
            return email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
        }

        /**
         * Validate website URL format
         */
        fun isValidWebsite(url: String?): Boolean {
            if (url.isNullOrBlank()) return true
            return url.matches(Regex("^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$"))
        }

        /**
         * Validate phone number format (basic validation)
         */
        fun isValidPhoneNumber(phone: String?): Boolean {
            if (phone.isNullOrBlank()) return true
            return phone.matches(Regex("^\\+?[1-9]\\d{1,14}$"))
        }

        /**
         * Create a new profile with required fields
         */
        fun create(
            username: String,
            displayName: String,
            email: String,
            avatarUrl: String,
            birthDate: Long
        ): UserProfile {
            require(isValidUsername(username)) { "Invalid username format" }
            require(displayName.isNotBlank()) { "Display name cannot be blank" }
            require(isValidEmail(email)) { "Invalid email format" }
            require(avatarUrl.isNotBlank()) { "Avatar URL cannot be blank" }

            return UserProfile(
                username = username.lowercase().trim(),
                displayName = displayName.trim(),
                email = email.lowercase().trim(),
                avatarUrl = avatarUrl,
                birthDate = birthDate
            )
        }
    }
}