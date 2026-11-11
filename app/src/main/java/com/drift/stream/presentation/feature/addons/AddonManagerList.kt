package com.drift.stream.presentation.feature.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.drift.stream.R
import com.drift.stream.domain.model.Addon
import com.drift.stream.domain.model.AddonCategory
import com.drift.stream.domain.model.AddonOwner
import com.drift.stream.domain.model.categoriesOf
import com.drift.stream.presentation.theme.NexusTheme

@Composable
fun AddonManagerList(
    uiState: AddonManagerUiState,
    onEvent: (AddonManagerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCat = uiState.selectedCategory
    val currentVisibleList =
        if (selectedCat == null) {
            uiState.flattenedVisibleAddons
        } else {
            uiState.visibleAddons
        }

    val visibleShared =
        remember(uiState.sharedWithMe, uiState.searchQuery, uiState.selectedCategory) {
            filterSharedAddons(uiState.sharedWithMe, uiState.searchQuery, uiState.selectedCategory)
        }

    when {
        isEmptyListState(uiState, visibleShared) -> {
            EmptyStateView(
                onAddCinemeta = { onEvent(AddonManagerEvent.AddCinemeta) },
                modifier = modifier,
            )
        }

        isSearchEmpty(uiState, currentVisibleList, visibleShared) -> {
            EmptySearchMatchesView(modifier = modifier)
        }

        selectedCat != null && isCategoryEmpty(uiState.visibleAddons, visibleShared) -> {
            EmptyCategoryView(
                category = selectedCat,
                onInstall = { onEvent(AddonManagerEvent.OpenStore(selectedCat)) },
                modifier = modifier,
            )
        }

        else -> {
            AddonLazyColumn(
                uiState = uiState,
                currentVisibleList = currentVisibleList,
                visibleShared = visibleShared,
                onEvent = onEvent,
                modifier = modifier,
            )
        }
    }
}

private fun filterSharedAddons(
    shared: List<Addon>,
    query: String,
    category: AddonCategory?,
): List<Addon> {
    val trimmed = query.trim()
    return shared.filter { addon ->
        val matchesCategory = category == null || category in categoriesOf(addon.manifest)
        val matchesQuery =
            trimmed.isEmpty() ||
                addon.manifest.name.contains(trimmed, ignoreCase = true) ||
                addon.manifest.description.contains(trimmed, ignoreCase = true)
        matchesCategory && matchesQuery
    }
}

private fun isEmptyListState(
    uiState: AddonManagerUiState,
    visibleShared: List<Addon>,
): Boolean = uiState.addons.isEmpty() && visibleShared.isEmpty() && !uiState.seedLoading

private fun isSearchEmpty(
    uiState: AddonManagerUiState,
    currentVisible: List<Addon>,
    visibleShared: List<Addon>,
): Boolean = uiState.searchQuery.isNotBlank() && currentVisible.isEmpty() && visibleShared.isEmpty()

private fun isCategoryEmpty(
    visibleAddons: List<Addon>,
    visibleShared: List<Addon>,
): Boolean = visibleAddons.isEmpty() && visibleShared.isEmpty()

@Composable
private fun AddonLazyColumn(
    uiState: AddonManagerUiState,
    currentVisibleList: List<Addon>,
    visibleShared: List<Addon>,
    onEvent: (AddonManagerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = NexusTheme.spacing

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.xxl * 3),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (uiState.selectedCategory == null) {
            for ((category, sectionAddons) in uiState.sections) {
                if (sectionAddons.isNotEmpty()) {
                    item(key = "header_${category.name}") {
                        AddonSectionHeader(category = category)
                    }
                    itemsIndexed(
                        items = sectionAddons,
                        key = { _, addon -> addon.transportUrl },
                    ) { _, addon ->
                        AddonItemRow(
                            addon = addon,
                            uiState = uiState,
                            visibleList = currentVisibleList,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.visibleAddons,
                key = { _, addon -> addon.transportUrl },
            ) { _, addon ->
                AddonItemRow(
                    addon = addon,
                    uiState = uiState,
                    visibleList = currentVisibleList,
                    onEvent = onEvent,
                )
            }
        }

        if (uiState.profileCount >= 2 && (visibleShared.isNotEmpty() || uiState.hiddenSharedCount > 0)) {
            item(key = "header_shared_with_you") {
                Text(
                    text = stringResource(R.string.addon_shared_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = spacing.md, bottom = spacing.xs),
                )
            }
            if (uiState.hiddenSharedCount > 0) {
                item(key = "note_hidden_shared") {
                    Text(
                        text = stringResource(R.string.addon_shared_hidden_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = spacing.xs),
                    )
                }
            }
            items(
                items = visibleShared,
                key = { addon -> "shared_${(addon.owner as? AddonOwner.Other)?.profileId}_${addon.manifest.id}" },
            ) { addon ->
                SharedAddonCard(
                    addon = addon,
                    isFamilyMode = uiState.isFamilyMode,
                    onToggleEnabled = { enabled ->
                        val ownerId = (addon.owner as? AddonOwner.Other)?.profileId ?: return@SharedAddonCard
                        onEvent(AddonManagerEvent.SetSharedEnabled(ownerId, addon.transportUrl, enabled))
                    },
                )
            }
        }
    }
}

@Composable
private fun AddonItemRow(
    addon: Addon,
    uiState: AddonManagerUiState,
    visibleList: List<Addon>,
    onEvent: (AddonManagerEvent) -> Unit,
) {
    val visibleIndex = visibleList.indexOfFirst { it.transportUrl == addon.transportUrl }
    AddonCard(
        addon = addon,
        isFirst = visibleIndex == 0,
        isLast = visibleIndex == visibleList.size - 1,
        failedRefresh = addon.manifest.name in uiState.failedRefreshAddons,
        profileCount = uiState.profileCount,
        onToggleShared = { shared -> onEvent(AddonManagerEvent.SetShared(addon.transportUrl, shared)) },
        onToggleEnabled = { enabled -> onEvent(AddonManagerEvent.SetEnabled(addon, enabled)) },
        onMoveUp = { onEvent(AddonManagerEvent.MoveUp(addon)) },
        onMoveDown = { onEvent(AddonManagerEvent.MoveDown(addon)) },
        onRefresh = { onEvent(AddonManagerEvent.Refresh(addon)) },
        onConfigure = { onEvent(AddonManagerEvent.OpenConfigure(addon)) },
        onCopyUrl = { onEvent(AddonManagerEvent.CopyUrl(addon)) },
        onRemove = { onEvent(AddonManagerEvent.RequestRemove(addon)) },
        isFamilyMode = uiState.isFamilyMode,
    )
}
