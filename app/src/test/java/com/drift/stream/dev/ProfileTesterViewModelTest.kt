package com.drift.stream.dev

import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.repository.ActiveProfileProvider
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.HistoryRepository
import com.drift.stream.domain.repository.LibraryRepository
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.ProgressRepository
import com.drift.stream.domain.repository.SearchHistoryRepository
import com.drift.stream.domain.repository.UpdateResult
import com.drift.stream.domain.repository.WatchlistRepository
import com.drift.stream.domain.usecase.NameResult
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTesterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeProfileRepository : ProfileRepository {
        val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
        override val profiles: Flow<List<Profile>> = profilesFlow

        override suspend fun ensureDefaultProfile(): Profile = error("unused")

        override suspend fun get(id: Long): Profile? = profilesFlow.value.firstOrNull { it.id == id }

        override suspend fun create(
            name: String,
            avatarColor: Int,
            avatarEmoji: String?,
        ): CreateResult {
            if (name.isBlank()) {
                return CreateResult.InvalidName(NameResult.Blank)
            }
            val nextId = (profilesFlow.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val profile =
                Profile(
                    id = nextId,
                    name = name,
                    avatarColor = avatarColor,
                    avatarEmoji = avatarEmoji,
                    isOwner = false,
                    needsSetup = false,
                    createdAt = 1000L,
                    lastUsedAt = 1000L,
                    sortOrder = 0,
                )
            profilesFlow.value = profilesFlow.value + profile
            return CreateResult.Created(profile)
        }

        override suspend fun update(profile: Profile): UpdateResult = error("unused")

        override suspend fun delete(id: Long): DeleteResult {
            val target = profilesFlow.value.firstOrNull { it.id == id }
            return when {
                target == null -> {
                    DeleteResult.NotFound
                }

                target.isOwner -> {
                    DeleteResult.RefusedOwner
                }

                else -> {
                    profilesFlow.value = profilesFlow.value.filterNot { it.id == id }
                    DeleteResult.Deleted
                }
            }
        }
    }

    private lateinit var profileRepo: FakeProfileRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder
    private lateinit var activeProfileProvider: FakeActiveProfileProvider
    private lateinit var watchlistRepo: WatchlistRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var searchHistoryRepo: SearchHistoryRepository
    private lateinit var libraryRepo: LibraryRepository
    private lateinit var progressRepo: ProgressRepository
    private lateinit var viewModel: ProfileTesterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        profileRepo = FakeProfileRepository()
        activeProfileHolder = mockk(relaxed = true)
        coEvery { activeProfileHolder.setActive(any()) } returns true
        activeProfileProvider = FakeActiveProfileProvider(1L)
        every { activeProfileHolder.activeProfileId } returns activeProfileProvider.activeProfileId

        watchlistRepo = mockk(relaxed = true)
        every { watchlistRepo.observeWatchlist() } returns flowOf(emptyList())

        historyRepo = mockk(relaxed = true)
        every { historyRepo.observeHistory(any()) } returns flowOf(emptyList())

        searchHistoryRepo = mockk(relaxed = true)
        every { searchHistoryRepo.observeHistory() } returns flowOf(emptyList())

        libraryRepo = mockk(relaxed = true)
        progressRepo = mockk(relaxed = true)

        val repos =
            ProfileTesterDataRepos(
                watchlistRepository = watchlistRepo,
                historyRepository = historyRepo,
                searchHistoryRepository = searchHistoryRepo,
                libraryRepository = libraryRepo,
                progressRepository = progressRepo,
            )

        viewModel =
            ProfileTesterViewModel(
                profileRepository = profileRepo,
                activeProfileHolder = activeProfileHolder,
                repos = repos,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun PTV1_state_listsProfilesAndMarksTheActiveOne() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value =
                listOf(
                    Profile(
                        id = 1L,
                        name = "Owner",
                        avatarColor = 0,
                        avatarEmoji = null,
                        isOwner = true,
                        needsSetup = false,
                        createdAt = 1000L,
                        lastUsedAt = 1000L,
                        sortOrder = 0,
                    ),
                    Profile(
                        id = 2L,
                        name = "Guest",
                        avatarColor = 1,
                        avatarEmoji = null,
                        isOwner = false,
                        needsSetup = false,
                        createdAt = 1000L,
                        lastUsedAt = 1000L,
                        sortOrder = 1,
                    ),
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.profiles.size)
            assertEquals(1L, state.activeId)
            assertTrue(state.profiles[0].isActive)
            assertTrue(state.profiles[0].isOwner)
            assertFalse(state.profiles[1].isActive)
        }

    @Test
    fun PTV2_create_blankName_showsTheReason() =
        runTest(testDispatcher) {
            viewModel.onEvent(ProfileTesterEvent.CreateProfile("   "))
            advanceUntilIdle()

            val message = viewModel.uiState.value.message
            assertTrue(message != null && message.contains("Blank"))
        }

    @Test
    fun PTV3_setActive_callsTheHolder_andDeleteOwnerShowsTheRefusal() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value =
                listOf(
                    Profile(
                        id = 1L,
                        name = "Owner",
                        avatarColor = 0,
                        avatarEmoji = null,
                        isOwner = true,
                        needsSetup = false,
                        createdAt = 1000L,
                        lastUsedAt = 1000L,
                        sortOrder = 0,
                    ),
                )
            advanceUntilIdle()

            viewModel.onEvent(ProfileTesterEvent.SetActive(2L))
            advanceUntilIdle()
            coVerify { activeProfileHolder.setActive(2L) }

            viewModel.onEvent(ProfileTesterEvent.DeleteProfile(1L))
            advanceUntilIdle()
            assertEquals("RefusedOwner", viewModel.uiState.value.message)
        }
}
