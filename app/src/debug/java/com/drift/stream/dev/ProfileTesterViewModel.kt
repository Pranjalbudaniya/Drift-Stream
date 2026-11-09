package com.drift.stream.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.ListStatus
import com.drift.stream.domain.model.MetaPreview
import com.drift.stream.domain.model.PosterShape
import com.drift.stream.domain.model.WatchProgress
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.HistoryRepository
import com.drift.stream.domain.repository.LibraryRepository
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.ProgressRepository
import com.drift.stream.domain.repository.SearchHistoryRepository
import com.drift.stream.domain.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileTesterItem(
    val id: Long,
    val name: String,
    val isOwner: Boolean,
    val isActive: Boolean,
)

data class ProfileTesterUiState(
    val profiles: List<ProfileTesterItem> = emptyList(),
    val activeId: Long? = null,
    val watchlistCount: Int = 0,
    val historyCount: Int = 0,
    val searchCount: Int = 0,
    val message: String? = null,
)

sealed interface ProfileTesterEvent {
    data class CreateProfile(
        val name: String,
    ) : ProfileTesterEvent

    data class SetActive(
        val id: Long,
    ) : ProfileTesterEvent

    data class DeleteProfile(
        val id: Long,
    ) : ProfileTesterEvent

    data object AddSampleData : ProfileTesterEvent

    data object ClearActiveData : ProfileTesterEvent
}

class ProfileTesterDataRepos
    @Inject
    constructor(
        val watchlistRepository: WatchlistRepository,
        val historyRepository: HistoryRepository,
        val searchHistoryRepository: SearchHistoryRepository,
        val libraryRepository: LibraryRepository,
        val progressRepository: ProgressRepository,
    )

@HiltViewModel
class ProfileTesterViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val activeProfileHolder: ActiveProfileHolder,
        private val repos: ProfileTesterDataRepos,
    ) : ViewModel() {
        private val messageFlow = MutableStateFlow<String?>(null)

        private val countsFlow =
            combine(
                repos.watchlistRepository.observeWatchlist(),
                repos.historyRepository.observeHistory(HISTORY_LIMIT),
                repos.searchHistoryRepository.observeHistory(),
            ) { watchlist, history, searches ->
                Triple(watchlist.size, history.size, searches.size)
            }

        val uiState: StateFlow<ProfileTesterUiState> =
            combine(
                profileRepository.profiles,
                activeProfileHolder.activeProfileId,
                countsFlow,
                messageFlow,
            ) { profiles, activeId, (wCount, hCount, sCount), message ->
                ProfileTesterUiState(
                    profiles =
                        profiles.map { p ->
                            ProfileTesterItem(
                                id = p.id,
                                name = p.name,
                                isOwner = p.isOwner,
                                isActive = p.id == activeId,
                            )
                        },
                    activeId = activeId,
                    watchlistCount = wCount,
                    historyCount = hCount,
                    searchCount = sCount,
                    message = message,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ProfileTesterUiState(),
            )

        fun onEvent(event: ProfileTesterEvent) {
            when (event) {
                is ProfileTesterEvent.CreateProfile -> createProfile(event.name)
                is ProfileTesterEvent.SetActive -> setActive(event.id)
                is ProfileTesterEvent.DeleteProfile -> deleteProfile(event.id)
                is ProfileTesterEvent.AddSampleData -> addSampleData()
                is ProfileTesterEvent.ClearActiveData -> clearActiveData()
            }
        }

        private fun createProfile(name: String) {
            viewModelScope.launch {
                val currentProfiles = uiState.value.profiles
                val nextColor = currentProfiles.size
                when (val result = profileRepository.create(name = name, avatarColor = nextColor, avatarEmoji = null)) {
                    is CreateResult.Created -> {
                        messageFlow.value = "Created ${result.profile.name}"
                    }

                    is CreateResult.InvalidName -> {
                        messageFlow.value = "InvalidName: ${result.reason}"
                    }
                }
            }
        }

        private fun setActive(id: Long) {
            viewModelScope.launch {
                val success = activeProfileHolder.setActive(id)
                messageFlow.value = if (success) "Active: $id" else "Failed to set active $id"
            }
        }

        private fun deleteProfile(id: Long) {
            viewModelScope.launch {
                val result = profileRepository.delete(id)
                messageFlow.value =
                    when (result) {
                        DeleteResult.Deleted -> "Deleted"
                        DeleteResult.RefusedOwner -> "RefusedOwner"
                        DeleteResult.RefusedLast -> "RefusedLast"
                        DeleteResult.RefusedActive -> "RefusedActive"
                        DeleteResult.NotFound -> "NotFound"
                    }
            }
        }

        private fun addSampleData() {
            viewModelScope.launch {
                val sampleMeta =
                    MetaPreview(
                        id = "dev:sample_title",
                        type = "movie",
                        name = "Sample Movie",
                        poster = null,
                        posterShape = PosterShape.POSTER,
                        background = null,
                        logo = null,
                        description = null,
                        releaseInfo = null,
                        imdbRating = null,
                        genres = emptyList(),
                    )
                repos.libraryRepository.setStatus(sampleMeta, ListStatus.PLAN_TO_WATCH)

                val sampleProgress =
                    WatchProgress(
                        key = "dev:sample_title|dev:sample_title",
                        metaId = "dev:sample_title",
                        videoId = "dev:sample_title",
                        type = "movie",
                        season = null,
                        episode = null,
                        positionMs = SAMPLE_POSITION_MS,
                        durationMs = SAMPLE_DURATION_MS,
                        watched = false,
                        updatedAt = System.currentTimeMillis(),
                    )
                repos.progressRepository.upsert(sampleProgress)

                repos.searchHistoryRepository.save("Sample Search")
                messageFlow.value = "Added sample data"
            }
        }

        private fun clearActiveData() {
            viewModelScope.launch {
                val entries = repos.watchlistRepository.observeWatchlist().first()
                for (entry in entries) {
                    val meta =
                        MetaPreview(
                            id = entry.metaId,
                            type = entry.type,
                            name = entry.name,
                            poster = entry.poster,
                            posterShape = PosterShape.POSTER,
                            background = entry.background,
                            logo = null,
                            description = null,
                            releaseInfo = null,
                            imdbRating = null,
                            genres = emptyList(),
                        )
                    repos.libraryRepository.setStatus(meta, null)
                }
                repos.historyRepository.clear()
                repos.searchHistoryRepository.clear()
                messageFlow.value = "Cleared active data"
            }
        }

        private companion object {
            private const val HISTORY_LIMIT = 500
            private const val SAMPLE_POSITION_MS = 60_000L
            private const val SAMPLE_DURATION_MS = 120_000L
        }
    }
