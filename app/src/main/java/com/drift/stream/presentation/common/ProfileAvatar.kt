package com.drift.stream.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.drift.stream.domain.profile.avatarInitial
import com.drift.stream.domain.profile.contentColorFor
import com.drift.stream.presentation.theme.tokens.AvatarTextRatio
import com.drift.stream.presentation.theme.tokens.avatarColorOf

@Composable
fun ProfileAvatar(
    colorIndex: Int,
    emoji: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    customColor: Int? = null,
    photoModel: Any? = null,
    memoryCacheKey: String? = null,
) {
    val fallbackColor = avatarColorOf(colorIndex)
    val backgroundColor = if (customColor != null) Color(customColor) else fallbackColor
    val backgroundArgb = customColor ?: fallbackColor.toArgb()
    val contentColor = Color(contentColorFor(backgroundArgb))
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
            color = contentColor,
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

        if (photoModel != null) {
            val context = LocalPlatformContext.current
            val request =
                remember(photoModel, memoryCacheKey) {
                    ImageRequest
                        .Builder(context)
                        .data(photoModel)
                        .apply {
                            if (memoryCacheKey != null) {
                                memoryCacheKey(memoryCacheKey)
                            }
                        }.crossfade(true)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
