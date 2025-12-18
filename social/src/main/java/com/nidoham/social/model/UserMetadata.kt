package com.nidoham.social.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.concurrent.TimeUnit

/**
 * Contains system-level metadata and account status.
 * Useful for auditing, account management, and security.
 *
 * @property createdAt Timestamp when account was created (milliseconds)
 * @property updatedAt Timestamp when account was last updated (milliseconds)
 * @property lastLoginAt Timestamp of last successful login (milliseconds)
 * @property lastLogoutAt Timestamp of last logout (milliseconds)
 * @property isVerified Whether the user's email/identity is verified
 * @property isActive Whether the account is active (not suspended/deleted)
 * @property isBanned Whether the account has been banned
 * @property isPrivate Whether the account is set to private mode
 * @property suspensionReason Optional reason for account suspension
 * @property suspendedUntil Optional timestamp for temporary suspension end (milliseconds)
 * @property emailVerifiedAt Timestamp when email was verified (milliseconds)
 * @property phoneVerifiedAt Timestamp when phone was verified (milliseconds)
 * @property twoFactorEnabled Whether 2FA is enabled
 * @property lastPasswordChangeAt Timestamp of last password change (milliseconds)
 * @property deviceId Last device ID used for login
 * @property ipAddress Last IP address used for login
 */
data class UserMetadata(
    @get:PropertyName("createdAt")
    @set:PropertyName("createdAt")
    var createdAt: Long? = null,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    var updatedAt: Long? = null,

    @get:PropertyName("lastLoginAt")
    @set:PropertyName("lastLoginAt")
    var lastLoginAt: Long? = null,

    @get:PropertyName("lastLogoutAt")
    @set:PropertyName("lastLogoutAt")
    var lastLogoutAt: Long? = null,

    @get:PropertyName("isVerified")
    @set:PropertyName("isVerified")
    var isVerified: Boolean = false,

    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,

    @get:PropertyName("isBanned")
    @set:PropertyName("isBanned")
    var isBanned: Boolean = false,

    @get:PropertyName("isPrivate")
    @set:PropertyName("isPrivate")
    var isPrivate: Boolean = false,

    @get:PropertyName("suspensionReason")
    @set:PropertyName("suspensionReason")
    var suspensionReason: String? = null,

    @get:PropertyName("suspendedUntil")
    @set:PropertyName("suspendedUntil")
    var suspendedUntil: Long? = null,

    @get:PropertyName("emailVerifiedAt")
    @set:PropertyName("emailVerifiedAt")
    var emailVerifiedAt: Long? = null,

    @get:PropertyName("phoneVerifiedAt")
    @set:PropertyName("phoneVerifiedAt")
    var phoneVerifiedAt: Long? = null,

    @get:PropertyName("twoFactorEnabled")
    @set:PropertyName("twoFactorEnabled")
    var twoFactorEnabled: Boolean = false,

    @get:PropertyName("lastPasswordChangeAt")
    @set:PropertyName("lastPasswordChangeAt")
    var lastPasswordChangeAt: Long? = null,

    @get:PropertyName("deviceId")
    @set:PropertyName("deviceId")
    var deviceId: String? = null,

    @get:PropertyName("ipAddress")
    @set:PropertyName("ipAddress")
    var ipAddress: String? = null
) {
    /**
     * No-arg constructor required by Firestore
     */
    constructor() : this(
        createdAt = null,
        updatedAt = null,
        lastLoginAt = null,
        lastLogoutAt = null
    )

    /**
     * Check if user is currently logged in
     * @return true if last login is more recent than last logout
     */
    @Exclude
    fun isLoggedIn(): Boolean {
        val login = lastLoginAt ?: return false
        val logout = lastLogoutAt ?: return true
        return login > logout
    }

    /**
     * Check if account is suspended
     * @return true if suspension is active and not expired
     */
    @Exclude
    fun isSuspended(): Boolean {
        val suspendedTo = suspendedUntil ?: return false
        return System.currentTimeMillis() < suspendedTo
    }

    /**
     * Check if account is accessible (active, not banned, not suspended)
     * @return true if account can be accessed
     */
    @Exclude
    fun isAccessible(): Boolean {
        return isActive && !isBanned && !isSuspended()
    }

    /**
     * Check if email is verified
     * @return true if email has been verified
     */
    @Exclude
    fun isEmailVerified(): Boolean = emailVerifiedAt != null

    /**
     * Check if phone is verified
     * @return true if phone has been verified
     */
    @Exclude
    fun isPhoneVerified(): Boolean = phoneVerifiedAt != null

    /**
     * Check if account is fully verified (email and identity)
     * @return true if account has completed verification
     */
    @Exclude
    fun isFullyVerified(): Boolean = isVerified && isEmailVerified()

    /**
     * Get account age in days
     * @return Number of days since account creation, or 0 if createdAt is null
     */
    @Exclude
    fun getAccountAgeInDays(): Long {
        val created = createdAt ?: return 0L
        val ageInMillis = System.currentTimeMillis() - created
        return TimeUnit.MILLISECONDS.toDays(ageInMillis)
    }

    /**
     * Get time since last login in hours
     * @return Hours since last login, or null if never logged in
     */
    @Exclude
    fun getHoursSinceLastLogin(): Long? {
        val login = lastLoginAt ?: return null
        val elapsed = System.currentTimeMillis() - login
        return TimeUnit.MILLISECONDS.toHours(elapsed)
    }

    /**
     * Get time since last activity in minutes
     * @return Minutes since last login or update, whichever is more recent
     */
    @Exclude
    fun getMinutesSinceLastActivity(): Long? {
        val login = lastLoginAt ?: 0L
        val update = updatedAt ?: 0L
        val lastActivity = maxOf(login, update)

        if (lastActivity == 0L) return null

        val elapsed = System.currentTimeMillis() - lastActivity
        return TimeUnit.MILLISECONDS.toMinutes(elapsed)
    }

    /**
     * Check if user is recently active (within last 5 minutes)
     * @return true if last activity was within 5 minutes
     */
    @Exclude
    fun isRecentlyActive(): Boolean {
        val minutes = getMinutesSinceLastActivity() ?: return false
        return minutes <= 5
    }

    /**
     * Check if user is online (logged in and active within 2 minutes)
     * @return true if user is considered online
     */
    @Exclude
    fun isOnline(): Boolean {
        if (!isLoggedIn()) return false
        val minutes = getMinutesSinceLastActivity() ?: return false
        return minutes <= 2
    }

    /**
     * Get remaining suspension time in days
     * @return Days remaining in suspension, or 0 if not suspended
     */
    @Exclude
    fun getRemainingSupensionDays(): Long {
        if (!isSuspended()) return 0L
        val suspendedTo = suspendedUntil ?: return 0L
        val remaining = suspendedTo - System.currentTimeMillis()
        return TimeUnit.MILLISECONDS.toDays(remaining)
    }

    /**
     * Check if password needs to be changed (older than 90 days)
     * @return true if password is older than 90 days
     */
    @Exclude
    fun needsPasswordChange(): Boolean {
        val lastChange = lastPasswordChangeAt ?: createdAt ?: return false
        val daysSinceChange = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - lastChange
        )
        return daysSinceChange > 90
    }

    /**
     * Mark account as updated
     * @return New UserMetadata with updated timestamp
     */
    fun markAsUpdated(): UserMetadata {
        return copy(updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark user as logged in
     * @param deviceId Optional device ID
     * @param ipAddress Optional IP address
     * @return New UserMetadata with login timestamp
     */
    fun markLogin(deviceId: String? = null, ipAddress: String? = null): UserMetadata {
        return copy(
            lastLoginAt = System.currentTimeMillis(),
            deviceId = deviceId ?: this.deviceId,
            ipAddress = ipAddress ?: this.ipAddress
        )
    }

    /**
     * Mark user as logged out
     * @return New UserMetadata with logout timestamp
     */
    fun markLogout(): UserMetadata {
        return copy(lastLogoutAt = System.currentTimeMillis())
    }

    /**
     * Mark email as verified
     * @return New UserMetadata with email verification timestamp
     */
    fun markEmailVerified(): UserMetadata {
        return copy(
            emailVerifiedAt = System.currentTimeMillis(),
            isVerified = isPhoneVerified() || emailVerifiedAt != null,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Mark phone as verified
     * @return New UserMetadata with phone verification timestamp
     */
    fun markPhoneVerified(): UserMetadata {
        return copy(
            phoneVerifiedAt = System.currentTimeMillis(),
            isVerified = isEmailVerified() || phoneVerifiedAt != null,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Enable two-factor authentication
     * @return New UserMetadata with 2FA enabled
     */
    fun enableTwoFactor(): UserMetadata {
        return copy(twoFactorEnabled = true, updatedAt = System.currentTimeMillis())
    }

    /**
     * Disable two-factor authentication
     * @return New UserMetadata with 2FA disabled
     */
    fun disableTwoFactor(): UserMetadata {
        return copy(twoFactorEnabled = false, updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark password as changed
     * @return New UserMetadata with password change timestamp
     */
    fun markPasswordChanged(): UserMetadata {
        return copy(
            lastPasswordChangeAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Suspend account
     * @param reason Reason for suspension
     * @param durationDays Duration of suspension in days
     * @return New UserMetadata with suspension details
     */
    fun suspend(reason: String, durationDays: Long): UserMetadata {
        require(reason.isNotBlank()) { "Suspension reason cannot be blank" }
        require(durationDays > 0) { "Suspension duration must be positive" }

        val suspendUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(durationDays)
        return copy(
            isActive = false,
            suspensionReason = reason,
            suspendedUntil = suspendUntil,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Lift suspension
     * @return New UserMetadata with suspension removed
     */
    fun liftSuspension(): UserMetadata {
        return copy(
            isActive = true,
            suspensionReason = null,
            suspendedUntil = null,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Ban account permanently
     * @param reason Reason for ban
     * @return New UserMetadata with ban applied
     */
    fun ban(reason: String): UserMetadata {
        require(reason.isNotBlank()) { "Ban reason cannot be blank" }
        return copy(
            isBanned = true,
            isActive = false,
            suspensionReason = reason,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Unban account
     * @return New UserMetadata with ban removed
     */
    fun unban(): UserMetadata {
        return copy(
            isBanned = false,
            isActive = true,
            suspensionReason = null,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Deactivate account
     * @return New UserMetadata with account deactivated
     */
    fun deactivate(): UserMetadata {
        return copy(isActive = false, updatedAt = System.currentTimeMillis())
    }

    /**
     * Reactivate account
     * @return New UserMetadata with account reactivated
     */
    fun reactivate(): UserMetadata {
        return copy(isActive = true, updatedAt = System.currentTimeMillis())
    }

    /**
     * Toggle privacy mode
     * @return New UserMetadata with privacy toggled
     */
    fun togglePrivacy(): UserMetadata {
        return copy(isPrivate = !isPrivate, updatedAt = System.currentTimeMillis())
    }

    companion object {
        /**
         * Create default metadata for a new user
         * @return UserMetadata with default values
         */
        fun createDefault(): UserMetadata {
            val now = System.currentTimeMillis()
            return UserMetadata(
                createdAt = now,
                updatedAt = now,
                lastLoginAt = now,
                isActive = true,
                isVerified = false
            )
        }
    }
}