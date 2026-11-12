package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.drift.stream.R
import com.drift.stream.domain.profile.isValidAvatarEmoji
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.theme.NexusTheme

private val TOP_BAR_HEIGHT = 56.dp

private object EmptyRecentEmojiProvider : RecentEmojiProvider {
    override fun recordSelection(emoji: String) = Unit

    override suspend fun getRecentEmojiList(): List<String> = emptyList()
}

@Composable
fun EmojiPickerScreen(
    onEmojiPicked: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {}
                .navigationBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(TOP_BAR_HEIGHT)
                    .padding(horizontal = NexusTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexusIconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.emoji_picker_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.width(NexusTheme.spacing.sm))

            Text(
                text = stringResource(R.string.emoji_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        AndroidView(
            factory = { context ->
                EmojiPickerView(context).apply {
                    setRecentEmojiProvider(EmptyRecentEmojiProvider)
                    setOnEmojiPickedListener { item ->
                        val emoji = item.emoji
                        if (isValidAvatarEmoji(emoji)) {
                            onEmojiPicked(emoji)
                            onDismiss()
                        }
                    }
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
    }
}

@Preview(name = "Emoji Picker - Light", showBackground = true)
@Composable
internal fun EmojiPickerScreenLightPreview() {
    NexusTheme {
        EmojiPickerScreen(
            onEmojiPicked = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Emoji Picker - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun EmojiPickerScreenDarkPreview() {
    NexusTheme {
        EmojiPickerScreen(
            onEmojiPicked = {},
            onDismiss = {},
        )
    }
}
