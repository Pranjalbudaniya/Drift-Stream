package com.drift.stream.presentation.feature.backup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.drift.stream.domain.backup.BackupExporter
import com.drift.stream.domain.backup.ExportOptions
import com.drift.stream.domain.backup.ExportResult
import com.drift.stream.domain.backup.ExportScope
import com.drift.stream.domain.backup.ExportSummary
import com.drift.stream.domain.backup.backupFileName
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import com.drift.stream.testutil.FakeActiveProfileProvider
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ExportDataViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testDate = LocalDate.of(2026, 11, 14)

    private lateinit var profileRepo: FakeProfileRepository
    private lateinit var fakeExporter: FakeBackupExporter
    private lateinit var activeHolder: FakeActiveProfileProvider
    private val fakeStream = ByteArrayOutputStream()
    private var deletedUri: Uri? = null

    private val ioOps =
        ExportIoOps(
            openStream = { fakeStream },
            deleteDocument = { uri ->
                deletedUri = uri
                true
            },
            dateProvider = { testDate },
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        profileRepo = FakeProfileRepository()
        fakeExporter = FakeBackupExporter()
        activeHolder = FakeActiveProfileProvider(1L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBackupExporter : BackupExporter {
        var exportCalls = 0
        var lastScope: ExportScope? = null
        var lastOptions: ExportOptions? = null
        var resultToReturn: ExportResult =
            ExportResult.Done(ExportSummary(1, 2, 3, 4, 5))

        override suspend fun export(
            scope: ExportScope,
            options: ExportOptions,
            sink: OutputStream,
        ): ExportResult {
            exportCalls++
            lastScope = scope
            lastOptions = options
            return resultToReturn
        }
    }

    private class FakeProfileRepository : ProfileRepository {
        val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
        override val profiles: Flow<List<Profile>> = profilesFlow

        override suspend fun ensureDefaultProfile(): Profile = error("unused")

        override suspend fun get(id: Long): Profile? = profilesFlow.value.firstOrNull { it.id == id }

        override suspend fun create(draft: ProfileDraft): CreateResult = error("unused")

        override suspend fun update(profile: Profile): UpdateResult = error("unused")

        override suspend fun completeSetup(
            id: Long,
            draft: ProfileDraft,
        ): UpdateResult = error("unused")

        override suspend fun setAvatarPhoto(
            profileId: Long,
            stamp: Long?,
        ) = error("unused")

        override suspend fun delete(id: Long): DeleteResult = error("unused")
    }

    private fun testProfile(
        id: Long,
        name: String,
        isOwner: Boolean = false,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = isOwner,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    private fun createViewModel(profileId: Long? = null): ExportDataViewModel {
        val handle =
            if (profileId != null) {
                SavedStateHandle(mapOf("profileId" to profileId))
            } else {
                SavedStateHandle()
            }
        return ExportDataViewModel(
            savedStateHandle = handle,
            backupExporter = fakeExporter,
            profileRepository = profileRepo,
            activeProfileHolder = activeHolder,
            ioDispatcher = testDispatcher,
            ioOps = ioOps,
        )
    }

    @Test
    fun EX1_init_nonOwnerExportingOther_closes() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            val nonOwner = testProfile(2L, "NonOwner", isOwner = false)
            profileRepo.profilesFlow.value = listOf(owner, nonOwner)
            activeHolder.setActive(2L)

            val vm = createViewModel(profileId = 1L)
            val effects = mutableListOf<ExportDataEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.effects.collect { effects.add(it) }
            }
            advanceUntilIdle()

            assertEquals(listOf(ExportDataEffect.Close), effects)
            assertTrue(vm.uiState.value.closeScreen)
        }

    @Test
    fun EX2_init_nonOwnerExportAll_closes() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            val nonOwner = testProfile(2L, "NonOwner", isOwner = false)
            profileRepo.profilesFlow.value = listOf(owner, nonOwner)
            activeHolder.setActive(2L)

            val vm = createViewModel(profileId = null)
            val effects = mutableListOf<ExportDataEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.effects.collect { effects.add(it) }
            }
            advanceUntilIdle()

            assertEquals(listOf(ExportDataEffect.Close), effects)
            assertTrue(vm.uiState.value.closeScreen)
        }

    @Test
    fun EX3_init_ownerExportAll_isAllowed() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            assertEquals(ExportStatus.Options, vm.uiState.value.status)
            assertFalse(vm.uiState.value.closeScreen)
        }

    @Test
    fun EX4_exportClicked_emitsPickerWithSuggestedName() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            val effects = mutableListOf<ExportDataEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.effects.collect { effects.add(it) }
            }

            vm.onEvent(ExportDataEvent.ExportClicked)
            advanceUntilIdle()

            assertEquals(1, effects.size)
            val effect = effects.first()
            assertTrue(effect is ExportDataEffect.LaunchFilePicker)
            val expectedName = backupFileName(ExportScope.All, null, testDate)
            assertEquals(expectedName, (effect as ExportDataEffect.LaunchFilePicker).suggestedName)
        }

    @Test
    fun EX5_fileChosen_runsExport_thenShowsSummary() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            val fakeUri = mockk<Uri>(relaxed = true)
            vm.onEvent(ExportDataEvent.FileChosen(fakeUri))
            advanceUntilIdle()

            assertEquals(1, fakeExporter.exportCalls)
            assertTrue(vm.uiState.value.status is ExportStatus.Done)
            val summary = (vm.uiState.value.status as ExportStatus.Done).summary
            assertEquals(1, summary.profiles)
            assertEquals(2, summary.libraryItems)
        }

    @Test
    fun EX6_fileChosen_null_isCancelled_nothingWritten() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            vm.onEvent(ExportDataEvent.FileChosen(null))
            advanceUntilIdle()

            assertEquals(0, fakeExporter.exportCalls)
            assertEquals(ExportStatus.Options, vm.uiState.value.status)
        }

    @Test
    fun EX7_writeFails_showsError_andRetryPossible() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)
            fakeExporter.resultToReturn = ExportResult.Failed(null)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            val fakeUri = mockk<Uri>(relaxed = true)
            vm.onEvent(ExportDataEvent.FileChosen(fakeUri))
            advanceUntilIdle()

            assertEquals(1, fakeExporter.exportCalls)
            assertTrue(vm.uiState.value.status is ExportStatus.Error)

            vm.onEvent(ExportDataEvent.Retry)
            assertEquals(ExportStatus.Options, vm.uiState.value.status)
        }

    @Test
    fun EX8_addonsCheckbox_showsWarning() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.showAddonsWarning)

            vm.onEvent(ExportDataEvent.AddonsToggled(true))
            assertTrue(vm.uiState.value.showAddonsWarning)

            vm.onEvent(ExportDataEvent.AddonsToggled(false))
            assertFalse(vm.uiState.value.showAddonsWarning)
        }

    @Test
    fun EX9_doubleExportClick_writesOnce() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            advanceUntilIdle()

            val fakeUri = mockk<Uri>(relaxed = true)
            vm.onEvent(ExportDataEvent.FileChosen(fakeUri))
            vm.onEvent(ExportDataEvent.FileChosen(fakeUri))
            advanceUntilIdle()

            assertEquals(1, fakeExporter.exportCalls)
        }

    @Test
    fun EX10_searchHistory_defaultsOn() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            assertTrue(vm.uiState.value.includeSearchHistory)
        }

    @Test
    fun EX11_addons_defaultsOff() =
        runTest(testDispatcher) {
            val owner = testProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel(profileId = null)
            assertFalse(vm.uiState.value.includeAddons)
        }
}
