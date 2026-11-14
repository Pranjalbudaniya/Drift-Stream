package com.drift.stream.presentation.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.drift.stream.data.profile.ActiveProfileHolder
import com.drift.stream.data.profile.AvatarPhotoPipeline
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.profile.DeleteHost
import com.drift.stream.domain.profile.PinChallenges
import com.drift.stream.domain.profile.ProfilePinManager
import com.drift.stream.domain.repository.ProfileRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
open class EditProfileTestFixture {
    @get:Rule
    val tempFolder = TemporaryFolder()

    val testDispatcher = StandardTestDispatcher()
    val testScope = TestScope(testDispatcher)

    val profileRepository = mockk<ProfileRepository>()
    val activeProfileHolder = mockk<ActiveProfileHolder>()
    val avatarPhotoStore = mockk<AvatarPhotoStore>(relaxed = true)
    val avatarPhotoPipeline = mockk<AvatarPhotoPipeline>(relaxed = true)
    val profilePinManager = mockk<ProfilePinManager>()
    val pinChallenges = mockk<PinChallenges>()

    val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    val activeProfileIdFlow = MutableStateFlow<Long?>(null)

    val fixedInstant: Instant = Instant.ofEpochMilli(1_700_000_000_000L)
    val fixedClock: Clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { profileRepository.profiles } returns profilesFlow
        every { activeProfileHolder.activeProfileId } returns activeProfileIdFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    fun sampleProfile(
        id: Long,
        name: String,
        stamp: Long? = null,
        isLocked: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            avatarPhotoStamp = stamp,
            isOwner = id == 1L,
            needsSetup = false,
            isLocked = isLocked,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    fun createViewModel(
        profileId: Long? = null,
        host: DeleteHost = DeleteHost.Picker,
        clock: Clock = fixedClock,
    ): EditProfileViewModel {
        val savedStateHandle =
            SavedStateHandle().apply {
                if (profileId != null) {
                    set("profileId", profileId)
                    set("host", host.name)
                }
            }
        return EditProfileViewModel(
            profileRepository = profileRepository,
            activeProfileHolder = activeProfileHolder,
            photoServices = AvatarPhotoServices(avatarPhotoStore, avatarPhotoPipeline),
            savedStateHandle = savedStateHandle,
            pinOps = ProfilePinOps(profilePinManager, pinChallenges),
            clock = clock,
        )
    }

    fun createAndInitViewModel(
        profileId: Long? = null,
        host: DeleteHost = DeleteHost.Picker,
        clock: Clock = fixedClock,
    ): EditProfileViewModel {
        val vm = createViewModel(profileId, host, clock)
        testScope.testScheduler.advanceUntilIdle()
        return vm
    }
}
