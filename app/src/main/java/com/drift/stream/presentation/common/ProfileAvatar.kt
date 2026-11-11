package com.drift.stream.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.drift.stream.domain.profile.avatarInitial
import com.drift.stream.presentation.theme.tokens.AvatarContent
import com.drift.stream.presentation.theme.tokens.AvatarTextRatio
import com.drift.stream.presentation.theme.tokens.avatarColorOf

@Composable
fun ProfileAvatar(
    colorIndex: Int,
    emoji: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = avatarColorOf(colorIndex)
    val displayText = if (!emoji.isNullOrBlank()) emoji else avatarInitial(name)
    val fontSize = with(LocalDensity.current) { (size * AvatarTextRatio).toSp() }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            color = AvatarContent,
            style =
                TextStyle(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
