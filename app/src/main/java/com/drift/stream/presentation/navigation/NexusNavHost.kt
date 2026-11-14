package com.drift.stream.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.drift.stream.domain.profile.DeleteHost
import com.drift.stream.domain.profile.canManageProfiles
import com.drift.stream.domain.profile.tabsToClearOnSwitch
import com.drift.stream.presentation.feature.addons.AddonManagerScreen
import com.drift.stream.presentation.feature.backup.ExportDataScreen
import com.drift.stream.presentation.feature.detail.DetailScreen
import com.drift.stream.presentation.feature.discover.DiscoverScreen
import com.drift.stream.presentation.feature.downloads.DownloadsScreen
import com.drift.stream.presentation.feature.home.HomeScreen
import com.drift.stream.presentation.feature.library.LibraryScreen
import com.drift.stream.presentation.feature.player.PlaybackSessionHolder
import com.drift.stream.presentation.feature.player.PlayerScreen
import com.drift.stream.presentation.feature.profile.AddProfileScreen
import com.drift.stream.presentation.feature.profile.EditProfileScreen
import com.drift.stream.presentation.feature.profile.ProfilesSettingsScreen
import com.drift.stream.presentation.feature.profile.ProfilesSettingsViewModel
import com.drift.stream.presentation.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.filter

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    playbackSessionHolder: PlaybackSessionHolder? = null,
    tabReselects: TabReselects = remember { TabReselects() },
) {
    LaunchedEffect(playbackSessionHolder) {
        playbackSessionHolder?.openRequests?.collect {
            if (navController.currentDestination?.hasRoute<Player>() != true) {
                navController.navigate(Player)
            }
        }
    }

    val discoverReselects =
        remember(tabReselects) {
            tabReselects.events.filter { it == TopLevelDestination.DISCOVER }
        }

    val libraryReselects =
        remember(tabReselects) {
            tabReselects.events.filter { it == TopLevelDestination.LIBRARY }
        }

    val downloadsReselects =
        remember(tabReselects) {
            tabReselects.events.filter { it == TopLevelDestination.DOWNLOADS }
        }

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        composable<Home> {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Settings) },
                onNavigateToAddons = { navController.navigate(Addons()) },
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onProfileSwitched = {
                    for (tab in tabsToClearOnSwitch()) {
                        navController.clearBackStack(tab.route)
                    }
                },
                onNavigateToAddProfile = { navController.navigate(ProfileAdd) },
            )
        }
        composable<Discover> {
            DiscoverScreen(
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onNavigateToAddons = { navController.navigate(Addons()) },
                reselectEvents = discoverReselects,
            )
        }
        composable<Downloads> {
            DownloadsScreen(
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onNavigateToDiscover = { navController.navigateToTopLevel(TopLevelDestination.DISCOVER) },
                onOpenSettings = { navController.navigate(Settings) },
                reselectEvents = downloadsReselects,
                playbackSessionHolder = playbackSessionHolder,
            )
        }
        composable<Library> {
            LibraryScreen(
                onItemClick = { type, id ->
                    navController.navigate(Detail(type = type, id = id))
                },
                onNavigateToDiscover = { navController.navigateToTopLevel(TopLevelDestination.DISCOVER) },
                reselectEvents = libraryReselects,
            )
        }
        composable<Settings> {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToAddons = { navController.navigate(Addons()) },
                onNavigateToProfiles = { navController.navigate(ProfilesList) },
                onNavigateToEditProfile = { profileId ->
                    navController.navigate(ProfileEdit(profileId = profileId))
                },
            )
        }
        composable<ProfilesList> {
            val viewModel: ProfilesSettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.activeProfile) {
                val active = state.activeProfile
                if (active != null && !canManageProfiles(active)) {
                    navController.navigateUp()
                }
            }
            ProfilesSettingsScreen(
                onNavigateUp = { navController.navigateUp() },
                onProfileClick = { profileId ->
                    navController.navigate(ProfileEdit(profileId = profileId))
                },
                onAddProfileClick = {
                    navController.navigate(ProfileAdd)
                },
                onExportAllClick = {
                    navController.navigate(ExportAll)
                },
                viewModel = viewModel,
            )
        }
        composable<ProfileEdit> { backStackEntry ->
            val args = backStackEntry.toRoute<ProfileEdit>()
            EditProfileScreen(
                profileId = args.profileId,
                host = DeleteHost.Settings,
                onBack = { navController.navigateUp() },
                onDone = { navController.navigateUp() },
                onDelete = { navController.navigateUp() },
                onExportClick = { profileId ->
                    navController.navigate(ExportProfile(profileId = profileId))
                },
            )
        }
        composable<ExportProfile> {
            ExportDataScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable<ExportAll> {
            ExportDataScreen(
                onBack = { navController.navigateUp() },
            )
        }
        composable<ProfileAdd> {
            val viewModel: ProfilesSettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.activeProfile) {
                val active = state.activeProfile
                if (active != null && !canManageProfiles(active)) {
                    navController.navigateUp()
                }
            }
            AddProfileScreen(
                onBack = { navController.navigateUp() },
                onDone = { navController.navigateUp() },
            )
        }
        composable<Addons> { backStackEntry ->
            val args = backStackEntry.toRoute<Addons>()
            AddonManagerScreen(
                onNavigateUp = { navController.navigateUp() },
                prefillUrl = args.prefillUrl,
                storeCategory = args.storeCategory,
            )
        }
        composable<Detail> {
            DetailScreen(
                onNavigateUp = { navController.navigateUp() },
                onOpenAddons = { navController.navigate(Addons()) },
                onOpenDownloads = {
                    navController.navigateToTopLevel(TopLevelDestination.DOWNLOADS)
                },
            )
        }
        composable<Player> {
            PlayerScreen(
                onNavigateUp = { navController.navigateUp() },
                onSwitchToNext = {
                    navController.navigate(Player) {
                        popUpTo<Player> { inclusive = true }
                    }
                },
                playbackSessionHolder = playbackSessionHolder,
            )
        }
    }
}
