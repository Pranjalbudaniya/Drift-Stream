package com.drift.stream.presentation.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.model.Profile
import com.drift.stream.presentation.common.ProfileAvatarFor

val PROFILE_AVATAR_BUTTON_SIZE = 36.dp
val PROFILE_AVATAR_TOUCH_TARGET = 48.dp

@Composable
fun ProfileAvatarButton(
    profile: Profile?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val clickLabel = stringResource(R.string.home_profile_avatar_click_label)
    val longClickLabel = stringResource(R.string.home_profile_avatar_long_click_label)
    val contentDesc =
        if (profile != null) {
            stringResource(R.string.home_profile_avatar_cd, profile.name)
        } else {
            clickLabel
        }

    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(PROFILE_AVATAR_TOUCH_TARGET)
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = clickLabel,
                    onLongClickLabel = longClickLabel,
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ).semantics {
                    this.contentDescription = contentDesc
                },
        contentAlignment = Alignment.Center,
    ) {
        if (profile != null) {
            ProfileAvatarFor(
                profile = profile,
                size = PROFILE_AVATAR_BUTTON_SIZE,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(PROFILE_AVATAR_BUTTON_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
