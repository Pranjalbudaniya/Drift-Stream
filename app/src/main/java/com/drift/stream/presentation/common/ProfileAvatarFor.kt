package com.drift.stream.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.drift.stream.data.profile.avatarPhotoFile
import com.drift.stream.domain.model.Profile

@Composable
fun ProfileAvatarFor(
    profile: Profile,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val photoModel =
        remember(profile.id, profile.avatarPhotoStamp) {
            if (profile.avatarPhotoStamp != null) {
                avatarPhotoFile(context.filesDir, profile.id)
            } else {
                null
            }
        }
    val memoryCacheKey =
        remember(profile.id, profile.avatarPhotoStamp) {
            if (profile.avatarPhotoStamp != null) {
                "avatar:${profile.id}:${profile.avatarPhotoStamp}"
            } else {
                null
            }
        }

    ProfileAvatar(
        colorIndex = profile.avatarColor,
        emoji = profile.avatarEmoji,
        name = profile.name,
        size = size,
        modifier = modifier,
        customColor = profile.avatarCustomColor,
        photoModel = photoModel,
        memoryCacheKey = memoryCacheKey,
    )
}
