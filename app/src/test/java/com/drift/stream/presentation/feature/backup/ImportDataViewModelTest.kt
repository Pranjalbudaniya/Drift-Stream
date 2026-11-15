package com.drift.stream.presentation.feature.backup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.drift.stream.data.backup.BackupFileReader
import com.drift.stream.data.backup.FileReadResult
import com.drift.stream.domain.backup.BackupImporter
import com.drift.stream.domain.backup.ImportMode
import com.drift.stream.domain.backup.ImportRequest
import com.drift.stream.domain.backup.ImportResult
import com.drift.stream.domain.backup.ImportSummary
import com.drift.stream.domain.backup.RejectReason
import com.drift.stream.domain.backup.ValidatedBackup
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ImportDataViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockUri: Uri = mockk(relaxed = true)

    private lateinit var profileRepo: FakeProfileRepository
    private lateinit var fakeReader: FakeBackupFileReader
    private lateinit var fakeImporter: FakeBackupImporter
    private lateinit var activeHolder: FakeActiveProfileProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        profileRepo = FakeProfileRepository()
        fakeReader = FakeBackupFileReader()
        fakeImporter = FakeBackupImporter()
        activeHolder = FakeActiveProfileProvider(1L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeBackupFileReader : BackupFileReader(mockk(relaxed = true), Dispatchers.Unconfined) {
        var readCalls = 0
        var resultToReturn: FileReadResult = FileReadResult.Success(createBackupJson().toByteArray())

        override suspend fun read(
            uri: Uri,
            maxBytes: Long,
        ): FileReadResult {
            readCalls++
            return resultToReturn
        }
    }

    private class FakeBackupImporter : BackupImporter {
        var importCalls = 0
        var lastBackup: ValidatedBackup? = null
        var lastRequest: ImportRequest? = null
        var resultToReturn: ImportResult =
            ImportResult.Done(ImportSummary(1, 10, 5, 2, 1, 0, emptyList()))

        override suspend fun import(
            backup: ValidatedBackup,
            request: ImportRequest,
        ): ImportResult {
            importCalls++
            lastBackup = backup
            lastRequest = request
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

    private fun sampleProfile(
        id: Long = 1L,
        name: String = "Alice",
        isOwner: Boolean = false,
    ) = Profile(
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

    private fun createViewModel(
        profileId: Long? = null,
        savedStateHandle: SavedStateHandle =
            if (profileId != null) {
                SavedStateHandle(mapOf("profileId" to profileId))
            } else {
                SavedStateHandle()
            },
    ): ImportDataViewModel =
        ImportDataViewModel(
            savedStateHandle = savedStateHandle,
            backupFileReader = fakeReader,
            backupImporter = fakeImporter,
            profileRepository = profileRepo,
            activeProfileHolder = activeHolder,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun IV1_init_nonOwnerAddAsNew_closes() =
        runTest(testDispatcher) {
            val nonOwner = sampleProfile(1L, "Bob", isOwner = false)
            profileRepo.profilesFlow.value = listOf(nonOwner)
            activeHolder.setActive(1L)

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.closeScreen)
        }

    @Test
    fun IV2_init_nonOwnerImportIntoOther_closes() =
        runTest(testDispatcher) {
            val target = sampleProfile(1L, "Alice", isOwner = false)
            val nonOwner = sampleProfile(2L, "Bob", isOwner = false)
            profileRepo.profilesFlow.value = listOf(target, nonOwner)
            activeHolder.setActive(2L)

            val vm = createViewModel(profileId = 1L)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.closeScreen)
        }

    @Test
    fun IV3_init_ownerAddAsNew_isAllowed() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            activeHolder.setActive(1L)

            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.closeScreen)
            assertEquals(ImportStatus.Choose, vm.uiState.value.status)
        }

    @Test
    fun IV4_pickFile_null_staysAtChoose() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(null))
            advanceUntilIdle()

            assertEquals(ImportStatus.Choose, vm.uiState.value.status)
            assertEquals(0, fakeReader.readCalls)
        }

    @Test
    fun IV5_pickFile_valid_showsPreview() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            fakeReader.resultToReturn = FileReadResult.Success(createBackupJson().toByteArray())

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertEquals(ImportStatus.Preview, vm.uiState.value.status)
            val preview = vm.uiState.value.preview
            assertTrue(preview != null)
            assertEquals(1, preview?.profiles?.size)
            assertEquals("Alice", preview?.profiles?.get(0)?.name)
        }

    @Test
    fun IV6_pickFile_notABackup_showsError() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            val json = createBackupJson(format = "other")
            fakeReader.resultToReturn = FileReadResult.Success(json.toByteArray())

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertEquals(ImportStatus.Rejected(RejectReason.NotABackup), vm.uiState.value.status)
        }

    @Test
    fun IV7_pickFile_tooLarge_showsError() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            fakeReader.resultToReturn = FileReadResult.TooLarge

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertEquals(ImportStatus.Rejected(RejectReason.TooLarge), vm.uiState.value.status)
        }

    @Test
    fun IV8_pickFile_newerVersion_showsError() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            val json = createBackupJson(backupVersion = 2)
            fakeReader.resultToReturn = FileReadResult.Success(json.toByteArray())

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertEquals(ImportStatus.Rejected(RejectReason.NewerVersion), vm.uiState.value.status)
        }

    @Test
    fun IV9_intoMode_defaultsToMerge() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val vm = createViewModel(profileId = 1L)
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertEquals(ImportStatus.Preview, vm.uiState.value.status)
            assertEquals(SelectedMode.Merge, vm.uiState.value.intoMode)
        }

    @Test
    fun IV10_replace_requiresConfirmation_beforeImporting() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val vm = createViewModel(profileId = 1L)
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.IntoModeSelected(SelectedMode.Replace))
            vm.onEvent(ImportDataEvent.ImportClicked)
            advanceUntilIdle()

            assertEquals(ImportStatus.ConfirmReplace, vm.uiState.value.status)
            assertEquals(0, fakeImporter.importCalls)
        }

    @Test
    fun IV11_replaceConfirmed_runsImportOnce() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val vm = createViewModel(profileId = 1L)
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.IntoModeSelected(SelectedMode.Replace))
            vm.onEvent(ImportDataEvent.ImportClicked)
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.ReplaceConfirmed)
            vm.onEvent(ImportDataEvent.ReplaceConfirmed)
            advanceUntilIdle()

            assertEquals(1, fakeImporter.importCalls)
            assertEquals(ImportMode.ReplaceIn(1L), fakeImporter.lastRequest?.mode)
        }

    @Test
    fun IV12_addAsNew_importsSelectedProfilesOnly() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val json = createBackupJson("Alice", "Bob", "Charlie")
            fakeReader.resultToReturn = FileReadResult.Success(json.toByteArray())

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.ProfileToggled(1))
            vm.onEvent(ImportDataEvent.ImportClicked)
            advanceUntilIdle()

            assertEquals(listOf(0, 2), fakeImporter.lastRequest?.selectedProfileIndexes)
        }

    @Test
    fun IV13_addonsCheckbox_shownOnlyWhenFileHasAddons_defaultsOn() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val jsonWithAddon = createBackupJsonWithAddons("Alice")
            fakeReader.resultToReturn = FileReadResult.Success(jsonWithAddon.toByteArray())

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertTrue(vm.uiState.value.hasAddons)
            assertTrue(vm.uiState.value.includeAddons)

            val jsonNoAddon = createBackupJson("Alice")
            fakeReader.resultToReturn = FileReadResult.Success(jsonNoAddon.toByteArray())

            val vm2 = createViewModel()
            advanceUntilIdle()

            vm2.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            assertFalse(vm2.uiState.value.hasAddons)
        }

    @Test
    fun IV14_import_failure_showsError_retryPossible() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            fakeImporter.resultToReturn = ImportResult.Failed(IOException("Disk full"))

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.ImportClicked)
            advanceUntilIdle()

            assertEquals(ImportStatus.Error, vm.uiState.value.status)

            vm.onEvent(ImportDataEvent.Retry)
            assertEquals(ImportStatus.Preview, vm.uiState.value.status)
        }

    @Test
    fun IV15_import_success_showsSummary() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)
            val expectedSummary = ImportSummary(1, 10, 5, 2, 1, 0, emptyList())
            fakeImporter.resultToReturn = ImportResult.Done(expectedSummary)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.ImportClicked)
            advanceUntilIdle()

            assertEquals(ImportStatus.Summary(expectedSummary), vm.uiState.value.status)
        }

    @Test
    fun IV16_doubleImportClick_importsOnce() =
        runTest(testDispatcher) {
            val owner = sampleProfile(1L, "Owner", isOwner = true)
            profileRepo.profilesFlow.value = listOf(owner)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.FileChosen(mockUri))
            advanceUntilIdle()

            vm.onEvent(ImportDataEvent.ImportClicked)
            vm.onEvent(ImportDataEvent.ImportClicked)
            advanceUntilIdle()

            assertEquals(1, fakeImporter.importCalls)
        }

    companion object {
        private fun createBackupJson(
            vararg names: String = arrayOf("Alice"),
            format: String = "drift-backup",
            backupVersion: Int = 1,
        ): String {
            val profileList = if (names.isEmpty()) arrayOf("Alice") else names
            val profs =
                profileList.joinToString(",") { name ->
                    """{"name":"$name","avatar":{"colorIndex":1},"library":[],"progress":[],"settings":[]}"""
                }
            val header = """{"format":"$format","backupVersion":$backupVersion,"appVersion":"1.0.0""""
            return """$header,"createdAt":1000,"profiles":[$profs]}"""
        }

        private fun createBackupJsonWithAddons(name: String): String {
            val u = "https://a/manifest.json"
            val addon =
                """{"transportUrl":"$u","id":"a","name":"A","version":"1.0","manifestJson":"{}","enabled":true,""" +
                    """"position":0,"source":"LOCAL","installedAt":100}"""
            val prof =
                """{"name":"$name","avatar":{"colorIndex":1},"library":[],"progress":[],"settings":[],"addons":[$addon]}"""
            val header = """{"format":"drift-backup","backupVersion":1,"appVersion":"1.0.0""""
            return """$header,"createdAt":1000,"profiles":[$prof]}"""
        }
    }
}
