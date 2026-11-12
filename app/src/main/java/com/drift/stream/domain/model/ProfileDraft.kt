package com.drift.stream.domain.model

data class ProfileDraft(
    val name: String,
    val avatarColor: Int,
    val avatarEmoji: String? = null,
    val avatarCustomColor: Int? = null,
    val avatarPhotoStamp: Long? = null,
)
