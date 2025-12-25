package com.nidoham.social.stories

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.firebase.firestore.IgnoreExtraProperties
import com.nidoham.social.user.User
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@IgnoreExtraProperties
@Entity(
    tableName = "stories",
    indices = [
        Index(name = "idx_stories_authorId", value = ["authorId"]),
        Index(name = "idx_stories_expiresAt", value = ["expiresAt"]),
        Index(name = "idx_stories_createdAt", value = ["createdAt"]),
        Index(name = "idx_stories_visibility", value = ["visibility"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["authorId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Story(
    @PrimaryKey
    val id: String = "",

    // ← @ColumnInfo(index = true) DELETE করা হয়েছে
    @SerialName("author_id")
    val authorId: String = "",

    @SerialName("content_type")
    val contentType: StoryType = StoryType.IMAGE,

    @SerialName("media_urls")
    val mediaUrls: List<String> = emptyList(),

    val caption: String? = null,
    val location: String? = null,

    @SerialName("bg_color")
    val backgroundColor: String? = null,

    @SerialName("aspect_ratio")
    val aspectRatio: Float = 9f / 16f,

    val duration: Int = 5,

    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @SerialName("expires_at")
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000),

    val visibility: StoryVisibility = StoryVisibility.PUBLIC,

    @SerialName("allowed_viewers")
    val allowedViewers: List<String> = emptyList(),

    @SerialName("views_count")
    val viewsCount: Int = 0,
    @SerialName("reactions_count")
    val reactionsCount: Int = 0,
    @SerialName("comments_count")
    val commentsCount: Int = 0,
    @SerialName("shares_count")
    val sharesCount: Int = 0,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    @SerialName("is_banned")
    val isBanned: Boolean = false,
    @SerialName("is_sponsored")
    val isSponsored: Boolean = false,
    @SerialName("is_pinned")
    val isPinned: Boolean = false
) : Parcelable

enum class StoryType {
    IMAGE, VIDEO, TEXT
}

enum class StoryVisibility {
    PUBLIC, FRIENDS, CLOSE_FRIENDS, PRIVATE, CUSTOM
}