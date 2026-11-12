package com.drift.stream.presentation.feature.profile

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drift.stream.R
import com.drift.stream.domain.model.Profile
import com.drift.stream.presentation.common.ProfileAvatar
import com.drift.stream.presentation.common.ProfileAvatarFor
import com.drift.stream.presentation.theme.NexusTheme

private val SWITCHER_AVATAR_SIZE = 48.dp
private val PROGRESS_INDICATOR_SIZE = 20.dp
private val PROGRESS_STROKE_WIDTH = 2.dp
private val BADGE_PADDING_HORIZONTAL = 8.dp
private val BADGE_PADDING_VERTICAL = 2.dp
private const val ADD_PROFILE_AVATAR_COLOR = 11

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(
    state: ProfileSwitcherUiState,
    onEvent: (ProfileSwitcherEvent) -> Unit,
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
        ProfileSwitcherContent(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ProfileSwitcherContent(
    state: ProfileSwitcherUiState,
    onEvent: (ProfileSwitcherEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    Column(
        modifier =
            modifier
                .padding(horizontal = spacing.md)
                .padding(bottom = spacing.xl),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.profile_switcher_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (state.switching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(PROGRESS_INDICATOR_SIZE),
                    strokeWidth = PROGRESS_STROKE_WIDTH,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items(items = state.profiles, key = { it.id }) { profile ->
                val isCurrent = profile.id == state.activeProfile?.id
                val ownerLabel = if (profile.isOwner) stringResource(R.string.profile_badge_owner) else null
                val currentLabel = if (isCurrent) stringResource(R.string.profile_badge_current) else null
                val rowCd = listOfNotNull(profile.name, ownerLabel, currentLabel).joinToString(", ")

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !state.switching,
                                role = Role.Button,
                                onClick = { onEvent(ProfileSwitcherEvent.ProfileTapped(profile.id)) },
                            ).semantics { this.contentDescription = rowCd }
                            .padding(vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatarFor(
                        profile = profile,
                        size = SWITCHER_AVATAR_SIZE,
                    )
                    Spacer(modifier = Modifier.width(spacing.md))
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (profile.isOwner) {
                        Surface(
                            shape = NexusTheme.shapes.chip,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(horizontal = spacing.xs),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_badge_owner),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.padding(
                                        horizontal = BADGE_PADDING_HORIZONTAL,
                                        vertical = BADGE_PADDING_VERTICAL,
                                    ),
                            )
                        }
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.profile_badge_current),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }
            }

            if (state.canAdd) {
                item(key = "add_profile") {
                    val addProfileLabel = stringResource(R.string.who_is_watching_add_profile)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !state.switching,
                                    role = Role.Button,
                                    onClick = { onEvent(ProfileSwitcherEvent.AddProfileTapped) },
                                ).semantics { this.contentDescription = addProfileLabel }
                                .padding(vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(
                            colorIndex = ADD_PROFILE_AVATAR_COLOR,
                            emoji = "+",
                            name = "+",
                            size = SWITCHER_AVATAR_SIZE,
                        )
                        Spacer(modifier = Modifier.width(spacing.md))
                        Text(
                            text = addProfileLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (state.error) {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(R.string.profile_switcher_error_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = spacing.xs),
            )
        }
    }
}

private fun sampleProfile(
    id: Long,
    name: String,
    isOwner: Boolean = false,
    color: Int = 0,
): Profile =
    Profile(
        id = id,
        name = name,
        avatarColor = color,
        avatarEmoji = null,
        isOwner = isOwner,
        needsSetup = false,
        pinHash = null,
        pinSalt = null,
        pinIterations = null,
        createdAt = 1000L,
        lastUsedAt = 1000L,
        sortOrder = id.toInt(),
    )

@Preview(name = "Profile Switcher - Owner (Light)", showBackground = true)
@Composable
internal fun ProfileSwitcherOwnerPreview() {
    val profiles =
        listOf(
            sampleProfile(1L, "Me", isOwner = true, color = 0),
            sampleProfile(2L, "Asha", isOwner = false, color = 2),
            sampleProfile(3L, "Dev", isOwner = false, color = 4),
        )
    NexusTheme {
        Surface {
            ProfileSwitcherContent(
                state =
                    ProfileSwitcherUiState(
                        profiles = profiles,
                        activeProfile = profiles.first(),
                        canAdd = true,
                    ),
                onEvent = {},
            )
        }
    }
}

@Preview(name = "Profile Switcher - Non-Owner (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun ProfileSwitcherNonOwnerPreview() {
    val profiles =
        listOf(
            sampleProfile(1L, "Me", isOwner = true, color = 0),
            sampleProfile(2L, "Asha", isOwner = false, color = 2),
            sampleProfile(3L, "Dev", isOwner = false, color = 4),
        )
    NexusTheme {
        Surface {
            ProfileSwitcherContent(
                state =
                    ProfileSwitcherUiState(
                        profiles = profiles,
                        activeProfile = profiles[1],
                        canAdd = false,
                    ),
                onEvent = {},
            )
        }
    }
}
