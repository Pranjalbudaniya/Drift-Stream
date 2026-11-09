package com.drift.stream.dev

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.drift.stream.R
import com.drift.stream.presentation.common.NexusButton
import com.drift.stream.presentation.common.NexusOutlinedButton
import com.drift.stream.presentation.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTesterScreen(
    state: ProfileTesterUiState,
    onEvent: (ProfileTesterEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    var nameInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_tester_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.addon_tester_back),
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
                    .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.profile_tester_name_hint)) },
                        singleLine = true,
                    )
                    NexusButton(
                        onClick = {
                            onEvent(ProfileTesterEvent.CreateProfile(nameInput))
                            nameInput = ""
                        },
                    ) {
                        Text(stringResource(R.string.profile_tester_create))
                    }
                }
            }

            item {
                Text(
                    text =
                        stringResource(
                            R.string.profile_tester_counts,
                            state.watchlistCount,
                            state.historyCount,
                            state.searchCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    NexusButton(
                        onClick = { onEvent(ProfileTesterEvent.AddSampleData) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.profile_tester_add_sample_data))
                    }
                    NexusOutlinedButton(
                        onClick = { onEvent(ProfileTesterEvent.ClearActiveData) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.profile_tester_clear_active_data))
                    }
                }
            }

            if (!state.message.isNullOrBlank()) {
                item {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(state.profiles, key = { it.id }) { profile ->
                ProfileItemRow(
                    profile = profile,
                    onSetActive = { onEvent(ProfileTesterEvent.SetActive(profile.id)) },
                    onDelete = { onEvent(ProfileTesterEvent.DeleteProfile(profile.id)) },
                )
            }
        }
    }
}

@Composable
private fun ProfileItemRow(
    profile: ProfileTesterItem,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val label =
                buildString {
                    append("${profile.name} (id=${profile.id})")
                    if (profile.isOwner) append(" [Owner]")
                    if (profile.isActive) append(" [Active]")
                }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        NexusOutlinedButton(
            onClick = onSetActive,
            enabled = !profile.isActive,
        ) {
            Text(stringResource(R.string.profile_tester_set_active))
        }
        NexusOutlinedButton(
            onClick = onDelete,
            enabled = !profile.isOwner,
        ) {
            Text(stringResource(R.string.profile_tester_delete))
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ProfileTesterScreenPreview() {
    NexusTheme {
        Surface {
            ProfileTesterScreen(
                state =
                    ProfileTesterUiState(
                        profiles =
                            listOf(
                                ProfileTesterItem(id = 1L, name = "Owner", isOwner = true, isActive = true),
                                ProfileTesterItem(id = 2L, name = "Guest", isOwner = false, isActive = false),
                            ),
                        activeId = 1L,
                        watchlistCount = 3,
                        historyCount = 5,
                        searchCount = 2,
                        message = "Ready",
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
