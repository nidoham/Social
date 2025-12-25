package com.nidoham.social.data.local

import androidx.room.ColumnInfo

data class DatabaseStats(
    @ColumnInfo(name = "totalStories") val totalStories: Int,
    @ColumnInfo(name = "activeStories") val activeStories: Int,
    @ColumnInfo(name = "expiredStories") val expiredStories: Int,
    @ColumnInfo(name = "bannedStories") val bannedStories: Int,
    @ColumnInfo(name = "deletedStories") val deletedStories: Int
)