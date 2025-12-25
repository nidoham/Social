package com.nidoham.social.data.local

import androidx.room.TypeConverter
import com.nidoham.social.user.UserGender
import com.nidoham.social.user.UserPrivacy
import com.nidoham.social.user.UserRole

/**
 * TypeConverters for Room Database.
 * Room cannot store Enums directly, so we convert them to Strings.
 */
class UserTypeConverters {

    // ================== UserRole Converter ==================
    @TypeConverter
    fun fromUserRole(role: UserRole): String {
        return role.name
    }

    @TypeConverter
    fun toUserRole(value: String): UserRole {
        return runCatching {
            UserRole.valueOf(value)
        }.getOrDefault(UserRole.USER) // Default fallback
    }

    // ================== UserPrivacy Converter ==================
    @TypeConverter
    fun fromUserPrivacy(privacy: UserPrivacy): String {
        return privacy.name
    }

    @TypeConverter
    fun toUserPrivacy(value: String): UserPrivacy {
        return runCatching {
            UserPrivacy.valueOf(value)
        }.getOrDefault(UserPrivacy.PUBLIC) // Default fallback
    }

    // ================== UserGender Converter ==================
    @TypeConverter
    fun fromUserGender(gender: UserGender): String {
        return gender.name
    }

    @TypeConverter
    fun toUserGender(value: String): UserGender {
        return runCatching {
            UserGender.valueOf(value)
        }.getOrDefault(UserGender.OTHER) // Default fallback
    }
}
