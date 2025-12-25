package com.nidoham.social.user

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

@IgnoreExtraProperties
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val birthDateMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val onlineAtMillis: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false,
    val isBanned: Boolean = false,
    val isPremium: Boolean = false,
    val privacy: UserPrivacy = UserPrivacy.PUBLIC,
    val role: UserRole = UserRole.USER,
    val gender: UserGender = UserGender.OTHER,
    val postsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0
) {
    @get:Exclude @get:Ignore
    val fullName: String
        get() = if (lastName.isBlank()) firstName else "$firstName $lastName"

    @get:Exclude @get:Ignore
    val birthDate: LocalDate?
        get() = birthDateMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    @get:Exclude @get:Ignore
    val createdAt: Instant get() = Instant.ofEpochMilli(createdAtMillis)

    @get:Exclude @get:Ignore
    val lastActive: Instant get() = Instant.ofEpochMilli(onlineAtMillis)

    @get:Exclude @get:Ignore
    val age: Int?
        get() = birthDate?.let { Period.between(it, LocalDate.now()).years }
}

enum class UserPrivacy { PUBLIC, PRIVATE, FRIENDS_ONLY }
enum class UserRole { ADMIN, MODERATOR, USER }
enum class UserGender { MALE, FEMALE, OTHER }
