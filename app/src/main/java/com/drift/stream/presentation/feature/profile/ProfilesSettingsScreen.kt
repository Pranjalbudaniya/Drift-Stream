package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drift.stream.R
import com.drift.stream.domain.model.Profile
import com.drift.stream.presentation.common.NexusCard
import com.drift.stream.presentation.common.NexusIconButton
import com.drift.stream.presentation.common.ProfileAvatar
import com.drift.stream.presentation.common.ProfileAvatarFor
import com.drift.stream.presentation.theme.NexusTheme

private val PROFILE_ROW_AVATAR_SIZE = 40.dp
private val BADGE_VERTICAL_PADDING = 2.dp
private val ROW_MIN_HEIGHT = 56.dp
private const val ADD_PROFILE_AVATAR_COLOR = 11

@Composable
fun ProfilesSettingsScreen(
    onNavigateUp: () -> Unit,
    onProfileClick: (Long) -> Unit,
    onAddProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfilesSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfilesSettingsContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onProfileClick = onProfileClick,
        onAddProfileClick = onAddProfileClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSettingsContent(
    uiState: ProfilesSettingsUiState,
    onNavigateUp: () -> Unit,
    onProfileClick: (Long) -> Unit,
    onAddProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profiles_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    NexusIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(uiState.profiles, key = { it.profile.id }) { item ->
                ProfileItemRow(
                    item = item,
                    onClick = { onProfileClick(item.profile.id) },
                )
            }

            item(key = "add_profile") {
                AddProfileRow(
                    onClick = onAddProfileClick,
                )
            }
        }
    }
}

@Composable
private fun ProfileItemRow(
    item: ProfileSettingsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    val profile = item.profile

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
                    .clickable(
                        role = Role.Button,
                        onClick = onClick,
                    ).padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                ProfileAvatarFor(
                    profile = profile,
                    size = PROFILE_ROW_AVATAR_SIZE,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (item.isOwner || item.isCurrent) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            modifier = Modifier.padding(top = spacing.xxs),
                        ) {
                            if (item.isOwner) {
                                Surface(
                                    shape = NexusTheme.shapes.chip,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_badge_owner),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = spacing.xs,
                                                vertical = BADGE_VERTICAL_PADDING,
                                            ),
                                    )
                                }
                            }

                            if (item.isCurrent) {
                                Surface(
                                    shape = NexusTheme.shapes.chip,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_badge_current),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = spacing.xs,
                                                vertical = BADGE_VERTICAL_PADDING,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(spacing.sm))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddProfileRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    NexusCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
                    .clickable(
                        role = Role.Button,
                        onClick = onClick,
                    ).padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                ProfileAvatar(
                    colorIndex = ADD_PROFILE_AVATAR_COLOR,
                    emoji = "+",
                    name = "+",
                    size = PROFILE_ROW_AVATAR_SIZE,
                )

                Text(
                    text = stringResource(R.string.who_is_watching_add_profile),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Profiles List - Light", showBackground = true)
@Composable
internal fun ProfilesSettingsScreenLightPreview() {
    NexusTheme {
        ProfilesSettingsContent(
            uiState =
                ProfilesSettingsUiState(
                    profiles =
                        listOf(
                            ProfileSettingsItem(
                                profile =
                                    Profile(
                                        id = 1L,
                                        name = "Me",
                                        avatarColor = 0,
                                        avatarEmoji = null,
                                        isOwner = true,
                                        needsSetup = false,
                                        createdAt = 0L,
                                        lastUsedAt = 0L,
                                        sortOrder = 0,
                                    ),
                                isOwner = true,
                                isCurrent = true,
                            ),
                            ProfileSettingsItem(
                                profile =
                                    Profile(
                                        id = 2L,
                                        name = "Asha",
                                        avatarColor = 4,
                                        avatarEmoji = "🦊",
                                        isOwner = false,
                                        needsSetup = false,
                                        createdAt = 0L,
                                        lastUsedAt = 0L,
                                        sortOrder = 1,
                                    ),
                                isOwner = false,
                                isCurrent = false,
                            ),
                        ),
                ),
            onNavigateUp = {},
            onProfileClick = {},
            onAddProfileClick = {},
        )
    }
}

@Preview(name = "Profiles List - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun ProfilesSettingsScreenDarkPreview() {
    NexusTheme {
        ProfilesSettingsContent(
            uiState =
                ProfilesSettingsUiState(
                    profiles =
                        listOf(
                            ProfileSettingsItem(
                                profile =
                                    Profile(
                                        id = 1L,
                                        name = "Me",
                                        avatarColor = 0,
                                        avatarEmoji = null,
                                        isOwner = true,
                                        needsSetup = false,
                                        createdAt = 0L,
                                        lastUsedAt = 0L,
                                        sortOrder = 0,
                                    ),
                                isOwner = true,
                                isCurrent = false,
                            ),
                            ProfileSettingsItem(
                                profile =
                                    Profile(
                                        id = 2L,
                                        name = "Asha",
                                        avatarColor = 4,
                                        avatarEmoji = "🦊",
                                        isOwner = false,
                                        needsSetup = false,
                                        createdAt = 0L,
                                        lastUsedAt = 0L,
                                        sortOrder = 1,
                                    ),
                                isOwner = false,
                                isCurrent = true,
                            ),
                        ),
                ),
            onNavigateUp = {},
            onProfileClick = {},
            onAddProfileClick = {},
        )
    }
}
