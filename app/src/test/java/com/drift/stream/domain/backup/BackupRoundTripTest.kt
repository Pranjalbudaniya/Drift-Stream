package com.drift.stream.domain.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drift.stream.data.backup.BackupDaos
import com.drift.stream.data.backup.BackupEnvironment
import com.drift.stream.data.backup.DefaultBackupExporter
import com.drift.stream.data.local.db.dao.FakeAddonDao
import com.drift.stream.data.local.db.dao.FakeLibraryDao
import com.drift.stream.data.local.db.dao.FakeProgressDao
import com.drift.stream.data.local.db.dao.FakeSearchHistoryDao
import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.AppInfoProvider
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRoundTripTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixedEpochMs = 1700000000000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(fixedEpochMs), ZoneOffset.UTC)

    private lateinit var profileRepo: FakeProfileRepository
    private lateinit var libraryDao: FakeLibraryDao
    private lateinit var progressDao: FakeProgressDao
    private lateinit var searchHistoryDao: FakeSearchHistoryDao
    private lateinit var addonDao: FakeAddonDao
    private lateinit var stores: ProfileDataStores
    private lateinit var avatarPhotoStore: FakeAvatarPhotoStore

    @Before
    fun setUp() {
        profileRepo = FakeProfileRepository()
        libraryDao = FakeLibraryDao()
        progressDao = FakeProgressDao()
        searchHistoryDao = FakeSearchHistoryDao()
        addonDao = FakeAddonDao()
        val (s, _) = createStores()
        stores = s
        avatarPhotoStore = FakeAvatarPhotoStore(tmpFolder.root)
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

    private class FakeAvatarPhotoStore(
        private val rootDir: File,
    ) : AvatarPhotoStore {
        override fun fileOf(profileId: Long): File = File(rootDir, "avatar_$profileId.jpg")

        override suspend fun write(
            profileId: Long,
            bytes: ByteArray,
        ) {
            fileOf(profileId).writeBytes(bytes)
        }

        override suspend fun delete(profileId: Long) {
            fileOf(profileId).delete()
        }
    }

    private class FakeAppInfoProvider(
        override val versionName: String? = "1.2.3",
        override val versionCode: Long = 100L,
    ) : AppInfoProvider

    private fun createStores(): Pair<ProfileDataStores, DataStore<Preferences>> {
        val legacyFile = File(tmpFolder.root, "settings.preferences_pb")
        val legacySettings: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher),
                produceFile = { legacyFile },
            )
        val stores =
            ProfileDataStores(
                fileFor = { name -> File(tmpFolder.root, "$name.preferences_pb") },
                newScope = { CoroutineScope(testDispatcher) },
                legacySettings = legacySettings,
            )
        return Pair(stores, legacySettings)
    }

    private fun createExporter(maxBytes: Long = 64 * 1024 * 1024L): DefaultBackupExporter =
        DefaultBackupExporter(
            profileRepository = profileRepo,
            daos = BackupDaos(libraryDao, progressDao, searchHistoryDao, addonDao),
            profileDataStores = stores,
            avatarPhotoStore = avatarPhotoStore,
            env = BackupEnvironment(FakeAppInfoProvider(), clock, testDispatcher, maxBytes),
        )

    private fun seedProfiles() {
        val p1 =
            Profile(
                id = 1L,
                name = "Asha",
                avatarColor = 1,
                avatarEmoji = "🚀",
                avatarPhotoStamp = 12345L,
                isOwner = true,
                needsSetup = false,
                createdAt = 1000L,
                lastUsedAt = 1000L,
                sortOrder = 1,
            )
        val p2 =
            Profile(
                id = 2L,
                name = "Ravi",
                avatarColor = 2,
                avatarEmoji = null,
                avatarPhotoStamp = null,
                isOwner = false,
                needsSetup = false,
                createdAt = 2000L,
                lastUsedAt = 2000L,
                sortOrder = 2,
            )
        profileRepo.profilesFlow.value = listOf(p1, p2)
    }

    private suspend fun seedLibraryAndProgress() {
        libraryDao.upsert(
            LibraryItemEntity(
                metaId = "tt123",
                type = "movie",
                name = "Test Movie",
                poster = "https://example.com/poster.jpg",
                background = null,
                inWatchlist = true,
                addedAt = 1000L,
                profileId = 1L,
            ),
        )
        libraryDao.upsert(
            LibraryItemEntity(
                metaId = "tt456",
                type = "series",
                name = "Test Series",
                poster = null,
                background = null,
                inWatchlist = false,
                addedAt = 1100L,
                profileId = 2L,
            ),
        )
        progressDao.upsert(
            WatchProgressEntity(
                key = "tt123:tt123",
                metaId = "tt123",
                videoId = "tt123",
                type = "movie",
                season = null,
                episode = null,
                positionMs = 5000L,
                durationMs = 10000L,
                watched = false,
                updatedAt = 1500L,
                profileId = 1L,
            ),
        )
        progressDao.upsert(
            WatchProgressEntity(
                key = "tt456:tt456:1:1",
                metaId = "tt456",
                videoId = "tt456:1:1",
                type = "series",
                season = 1,
                episode = 1,
                positionMs = 2000L,
                durationMs = 3000L,
                watched = false,
                updatedAt = 1600L,
                profileId = 2L,
            ),
        )
    }

    private suspend fun seedSearchAndAddons() {
        searchHistoryDao.upsert(SearchHistoryEntity(query = "action movies", lastUsedAt = 2000L, profileId = 1L))
        searchHistoryDao.upsert(SearchHistoryEntity(query = "comedy", lastUsedAt = 2100L, profileId = 2L))
        addonDao.upsert(
            AddonEntity(
                transportUrl = "https://example.com/manifest.json",
                id = "org.example",
                name = "Example Addon",
                version = "1.0",
                manifestJson = "{\"id\":\"org.example\",\"name\":\"Example Addon\"}",
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 1000L,
                profileId = 1L,
            ),
        )
        addonDao.upsert(
            AddonEntity(
                transportUrl = "https://example.com/manifest2.json",
                id = "org.example2",
                name = "Example 2",
                version = "1.0",
                manifestJson = "{\"id\":\"org.example2\",\"name\":\"Example 2\"}",
                enabled = true,
                position = 0,
                source = AddonSource.STREMIO_ACCOUNT,
                installedAt = 1000L,
                profileId = 2L,
            ),
        )
    }

    private suspend fun seedStores() {
        stores.storeFor(PersonalStore.SETTINGS, 1L).edit {
            it[stringPreferencesKey("theme_mode")] = "dark"
            it[booleanPreferencesKey("amoled")] = true
        }
        stores.storeFor(PersonalStore.SETTINGS, 2L).edit {
            it[stringPreferencesKey("theme_mode")] = "light"
        }
    }

    private fun assertProfile1(
        prof1: ValidatedProfile,
        validJpeg: ByteArray,
    ) {
        assertEquals("Asha", prof1.name)
        assertEquals(1, prof1.avatar.colorIndex)
        assertEquals("🚀", prof1.avatar.emoji)
        assertNotNull(prof1.avatar.photoJpegBytes)
        assertArrayEquals(validJpeg, prof1.avatar.photoJpegBytes)
        assertEquals(1, prof1.library.size)
        assertEquals("tt123", prof1.library[0].metaId)
        assertEquals("Test Movie", prof1.library[0].name)
        assertEquals("https://example.com/poster.jpg", prof1.library[0].poster)
        assertEquals(true, prof1.library[0].inWatchlist)
        assertEquals(1, prof1.progress.size)
        assertEquals("tt123", prof1.progress[0].metaId)
        assertEquals(5000L, prof1.progress[0].positionMs)
        assertNotNull(prof1.searchHistory)
        assertEquals(1, prof1.searchHistory?.size)
        assertEquals("action movies", prof1.searchHistory?.get(0)?.query)
        assertNotNull(prof1.addons)
        assertEquals(1, prof1.addons?.size)
        assertEquals("https://example.com/manifest.json", prof1.addons?.get(0)?.transportUrl)
        val settings1 = prof1.settings[PersonalStore.SETTINGS.legacyFileName]
        assertNotNull(settings1)
        assertEquals("dark", settings1?.get("theme_mode"))
        assertEquals(true, settings1?.get("amoled"))
    }

    private fun assertProfile2(prof2: ValidatedProfile) {
        assertEquals("Ravi", prof2.name)
        assertEquals(2, prof2.avatar.colorIndex)
        assertNull(prof2.avatar.emoji)
        assertNull(prof2.avatar.photoJpegBytes)
        assertEquals(1, prof2.library.size)
        assertEquals("tt456", prof2.library[0].metaId)
        assertEquals(1, prof2.progress.size)
        assertEquals("tt456", prof2.progress[0].metaId)
        assertNotNull(prof2.searchHistory)
        assertEquals(1, prof2.searchHistory?.size)
        assertEquals("comedy", prof2.searchHistory?.get(0)?.query)
        assertNotNull(prof2.addons)
        assertEquals(1, prof2.addons?.size)
        assertEquals("https://example.com/manifest2.json", prof2.addons?.get(0)?.transportUrl)
        val settings2 = prof2.settings[PersonalStore.SETTINGS.legacyFileName]
        assertNotNull(settings2)
        assertEquals("light", settings2?.get("theme_mode"))
    }

    @Test
    fun RT1_export_thenParse_isLossless() =
        runTest {
            val validJpeg =
                byteArrayOf(
                    0xFF.toByte(),
                    0xD8.toByte(),
                    0xFF.toByte(),
                    0xE0.toByte(),
                    0x00,
                    0x10,
                    0x4A,
                    0x46,
                    0x49,
                    0x46,
                    0x00,
                    0x01,
                )
            avatarPhotoStore.write(1L, validJpeg)
            seedProfiles()
            seedLibraryAndProgress()
            seedSearchAndAddons()
            seedStores()

            val exporter = createExporter()
            val out = ByteArrayOutputStream()
            val exportResult =
                exporter.export(
                    scope = ExportScope.All,
                    options = ExportOptions(includeSearchHistory = true, includeAddons = true),
                    sink = out,
                )

            assertTrue(exportResult is ExportResult.Done)
            println("--- RT1 BACKUP JSON START ---")
            println(out.toString(Charsets.UTF_8.name()))
            println("--- RT1 BACKUP JSON END ---")

            val parseOutcome = BackupParser.parse(out.toByteArray())
            assertTrue(parseOutcome is ParseOutcome.Ready)
            val ready = parseOutcome as ParseOutcome.Ready

            assertEquals(0, ready.skipped)
            assertEquals(2, ready.backup.profiles.size)
            assertProfile1(ready.backup.profiles[0], validJpeg)
            assertProfile2(ready.backup.profiles[1])
        }

    private suspend fun seedProfileForRT2() {
        val p1 =
            Profile(
                id = 1L,
                name = "Asha",
                avatarColor = 1,
                avatarEmoji = null,
                avatarPhotoStamp = null,
                isOwner = true,
                needsSetup = false,
                createdAt = 1000L,
                lastUsedAt = 1000L,
                sortOrder = 1,
            )
        profileRepo.profilesFlow.value = listOf(p1)

        libraryDao.upsert(
            LibraryItemEntity(
                metaId = "tt123",
                type = "movie",
                name = "Test Movie",
                poster = null,
                background = null,
                inWatchlist = true,
                addedAt = 1000L,
                profileId = 1L,
            ),
        )
        progressDao.upsert(
            WatchProgressEntity(
                key = "tt123:tt123",
                metaId = "tt123",
                videoId = "tt123",
                type = "movie",
                season = null,
                episode = null,
                positionMs = 5000L,
                durationMs = 10000L,
                watched = false,
                updatedAt = 1500L,
                profileId = 1L,
            ),
        )
        searchHistoryDao.upsert(SearchHistoryEntity(query = "query", lastUsedAt = 1000L, profileId = 1L))
        addonDao.upsert(
            AddonEntity(
                transportUrl = "https://example.com/manifest.json",
                id = "org.example",
                name = "Example Addon",
                version = "1.0",
                manifestJson = "{}",
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL,
                installedAt = 1000L,
                profileId = 1L,
            ),
        )
    }

    @Test
    fun RT2_export_withoutOptions_nullListsStayNull() =
        runTest {
            seedProfileForRT2()

            val exporter = createExporter()
            val out = ByteArrayOutputStream()
            val exportResult =
                exporter.export(
                    scope = ExportScope.All,
                    options = ExportOptions(includeSearchHistory = false, includeAddons = false),
                    sink = out,
                )

            assertTrue(exportResult is ExportResult.Done)

            val parseOutcome = BackupParser.parse(out.toByteArray())
            assertTrue(parseOutcome is ParseOutcome.Ready)
            val ready = parseOutcome as ParseOutcome.Ready

            val prof = ready.backup.profiles[0]
            assertEquals("Asha", prof.name)
            assertEquals(1, prof.library.size)
            assertEquals(1, prof.progress.size)
            assertNull(prof.searchHistory)
            assertNull(prof.addons)
        }
}
