package com.nidoham.social.posts

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
    tableName = "posts",
    indices = [
        Index(name = "idx_posts_authorId", value = ["authorId"]),
        Index(name = "idx_posts_createdAt", value = ["createdAt"]),
        Index(name = "idx_posts_visibility", value = ["visibility"])
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
data class Post(
    @PrimaryKey
    val id: String = "",

    // ← @ColumnInfo(index = true) DELETE করা হয়েছে
    @SerialName("author_id")
    val authorId: String = "",

    val content: String = "",

    @SerialName("content_type")
    val contentType: PostType = PostType.TEXT,

    @SerialName("media_urls")
    val mediaUrls: List<String> = emptyList(),

    val location: String? = null,

    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @SerialName("updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    val visibility: PostVisibility = PostVisibility.PUBLIC,
    val status: PostStatus = PostStatus.ACTIVE,

    @SerialName("likes_count")
    val likesCount: Int = 0,

    @SerialName("comments_count")
    val commentsCount: Int = 0,

    @SerialName("shares_count")
    val sharesCount: Int = 0,

    @SerialName("views_count")
    val viewsCount: Int = 0,

    @SerialName("is_deleted")
    val isDeleted: Boolean = false,

    @SerialName("is_sponsored")
    val isSponsored: Boolean = false,

    @SerialName("is_pinned")
    val isPinned: Boolean = false,

    @SerialName("is_banned")
    val isBanned: Boolean = false,

    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList()
) : Parcelable {
    val hasMedia: Boolean get() = mediaUrls.isNotEmpty()
}

enum class PostType {
    TEXT, IMAGE, VIDEO, LINK, POLL
}

enum class PostVisibility {
    PUBLIC, FRIENDS, PRIVATE
}

enum class PostStatus {
    ACTIVE, ARCHIVED, DRAFT
}