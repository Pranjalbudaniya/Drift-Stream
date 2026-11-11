package com.drift.stream.presentation.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.drift.stream.domain.profile.isValidAvatarEmoji
import com.drift.stream.presentation.theme.NexusTheme

private val EMOJI_SHEET_HEIGHT = 360.dp

private object EmptyRecentEmojiProvider : RecentEmojiProvider {
    override fun recordSelection(emoji: String) {
        // No-op: recent emojis are not persisted or shared across profiles
    }

    override suspend fun getRecentEmojiList(): List<String> = emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onEmojiPicked: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = NexusTheme.shapes.card,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(EMOJI_SHEET_HEIGHT)
                    .padding(bottom = NexusTheme.spacing.md),
        ) {
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
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
