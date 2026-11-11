package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.model.Profile
import com.drift.stream.presentation.common.ProfileAvatar
import com.drift.stream.presentation.theme.NexusTheme
import com.drift.stream.presentation.theme.tokens.AvatarTileSize

@Composable
fun WhoIsWatchingScreen(
    profiles: List<Profile>,
    isChoosing: Boolean,
    chooseFailed: Boolean,
    onProfileChosen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAddProfile: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(NexusTheme.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.who_is_watching_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (chooseFailed) {
                    Spacer(modifier = Modifier.height(NexusTheme.spacing.xs))
                    Text(
                        text = stringResource(R.string.who_is_watching_choose_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(NexusTheme.spacing.xxl))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            NexusTheme.spacing.md,
                            Alignment.CenterHorizontally,
                        ),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.lg),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileTile(
                            profile = profile,
                            enabled = !isChoosing,
                            onClick = { onProfileChosen(profile.id) },
                        )
                    }

                    if (onAddProfile != null) {
                        item(key = "add_profile") {
                            AddProfileTile(
                                enabled = !isChoosing,
                                onClick = onAddProfile,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .semantics { this.contentDescription = profile.name }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(NexusTheme.spacing.xs),
    ) {
        ProfileAvatar(
            colorIndex = profile.avatarColor,
            emoji = profile.avatarEmoji,
            name = profile.name,
            size = AvatarTileSize,
        )
        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val ADD_PROFILE_AVATAR_COLOR = 11
private const val PREVIEW_AVATAR_COLOR = 3

@Composable
fun AddProfileTile(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addLabel = stringResource(R.string.who_is_watching_add_profile)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .semantics { this.contentDescription = addLabel }
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(NexusTheme.spacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(
                colorIndex = ADD_PROFILE_AVATAR_COLOR,
                emoji = "+",
                name = "+",
                size = AvatarTileSize,
            )
        }
        Spacer(modifier = Modifier.height(NexusTheme.spacing.sm))
        Text(
            text = addLabel,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "Who Is Watching - Light", showBackground = true)
@Composable
internal fun WhoIsWatchingScreenLightPreview() {
    NexusTheme {
        WhoIsWatchingScreen(
            profiles =
                listOf(
                    Profile(
                        1L,
                        "Me",
                        0,
                        null,
                        isOwner = true,
                        needsSetup = false,
                        createdAt = 0,
                        lastUsedAt = 0,
                        sortOrder = 0,
                    ),
                    Profile(
                        2L,
                        "Asha",
                        PREVIEW_AVATAR_COLOR,
                        "🦊",
                        isOwner = false,
                        needsSetup = false,
                        createdAt = 0,
                        lastUsedAt = 0,
                        sortOrder = 1,
                    ),
                ),
            isChoosing = false,
            chooseFailed = false,
            onProfileChosen = {},
        )
    }
}

@Preview(name = "Who Is Watching - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun WhoIsWatchingScreenDarkPreview() {
    NexusTheme {
        WhoIsWatchingScreen(
            profiles =
                listOf(
                    Profile(
                        1L,
                        "Me",
                        0,
                        null,
                        isOwner = true,
                        needsSetup = false,
                        createdAt = 0,
                        lastUsedAt = 0,
                        sortOrder = 0,
                    ),
                    Profile(
                        2L,
                        "Asha",
                        PREVIEW_AVATAR_COLOR,
                        "🦊",
                        isOwner = false,
                        needsSetup = false,
                        createdAt = 0,
                        lastUsedAt = 0,
                        sortOrder = 1,
                    ),
                ),
            isChoosing = false,
            chooseFailed = true,
            onProfileChosen = {},
        )
    }
}
