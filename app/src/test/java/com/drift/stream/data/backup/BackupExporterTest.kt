package com.drift.stream.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.drift.stream.domain.backup.BACKUP_FORMAT
import com.drift.stream.domain.backup.BACKUP_VERSION
import com.drift.stream.domain.backup.ExportOptions
import com.drift.stream.domain.backup.ExportResult
import com.drift.stream.domain.backup.ExportScope
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class BackupExporterTest {
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

    private fun testProfile(
        id: Long,
        name: String,
    ): Profile =
        Profile(
            id = id,
            name = name,
            avatarColor = 0,
            avatarEmoji = null,
            isOwner = false,
            needsSetup = false,
            createdAt = 1000L,
            lastUsedAt = 1000L,
            sortOrder = id.toInt(),
        )

    private fun sampleLibrary(
        id: String,
        inWatchlist: Boolean,
    ) = LibraryItemEntity(id, "movie", "Movie $id", null, null, inWatchlist, 100L, profileId = 1L)

    private fun sampleProgress(
        key: String,
        watched: Boolean,
    ) = WatchProgressEntity(key, "m", "v", "movie", null, null, 100L, 200L, watched, 10L, 1L)

    private fun sampleAddon(
        url: String,
        id: String,
        profileId: Long = 1L,
        shared: Boolean = false,
    ) = AddonEntity(url, id, id, "1.0", "{\"id\":\"$id\"}", true, 0, AddonSource.LOCAL, 100L, profileId, shared)

    @Test
    fun BE1_exportProfile_includesLibraryProgressSettingsAndAvatar() =
        runTest(testDispatcher) {
            val profile = testProfile(1L, "Alice").copy(avatarColor = 2, avatarEmoji = "⭐", isOwner = true)
            profileRepo.profilesFlow.value = listOf(profile)
            libraryDao.upsert(sampleLibrary("m1", inWatchlist = true))
            libraryDao.upsert(sampleLibrary("m2", inWatchlist = false))
            progressDao.upsert(sampleProgress("k1", false))
            progressDao.upsert(sampleProgress("k2", true))
            progressDao.upsert(sampleProgress("k3", false))
            stores.storeFor(PersonalStore.SETTINGS, 1L).edit { it[stringPreferencesKey("theme_mode")] = "dark" }

            val sink = ByteArrayOutputStream()
            val result =
                createExporter().export(
                    ExportScope.Profile(1L),
                    ExportOptions(includeSearchHistory = false, includeAddons = false),
                    sink,
                )

            assertTrue(result is ExportResult.Done)
            val done = result as ExportResult.Done
            assertEquals(1, done.summary.profiles)
            assertEquals(2, done.summary.libraryItems)
            assertEquals(3, done.summary.progressEntries)

            val json = Json.parseToJsonElement(sink.toString(Charsets.UTF_8.name())).jsonObject
            val p0 = json["profiles"]!!.jsonArray[0].jsonObject
            assertEquals("Alice", p0["name"]!!.jsonPrimitive.content)
            assertEquals(2, p0["library"]!!.jsonArray.size)
            assertEquals(3, p0["progress"]!!.jsonArray.size)
            assertNotNull(p0["settings"])
        }

    @Test
    fun BE2_searchHistory_excludedWhenNotChosen() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))
            searchHistoryDao.upsert(SearchHistoryEntity("dune", 100L, 1L))

            val sinkNoSearch = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sinkNoSearch,
            )
            val p0NoSearch =
                Json
                    .parseToJsonElement(sinkNoSearch.toString(Charsets.UTF_8.name()))
                    .jsonObject["profiles"]!!
                    .jsonArray[0]
                    .jsonObject
            assertNull(p0NoSearch["searchHistory"])

            val sinkWithSearch = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = true, includeAddons = false),
                sinkWithSearch,
            )
            val p0WithSearch =
                Json
                    .parseToJsonElement(sinkWithSearch.toString(Charsets.UTF_8.name()))
                    .jsonObject["profiles"]!!
                    .jsonArray[0]
                    .jsonObject
            assertNotNull(p0WithSearch["searchHistory"])
            assertEquals(1, p0WithSearch["searchHistory"]!!.jsonArray.size)
        }

    @Test
    fun BE3_addons_excludedByDefault() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))
            addonDao.upsert(sampleAddon("https://example.com/manifest.json", "id1"))

            val sink = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sink,
            )

            val p0 =
                Json
                    .parseToJsonElement(sink.toString(Charsets.UTF_8.name()))
                    .jsonObject["profiles"]!!
                    .jsonArray[0]
                    .jsonObject
            assertNull(p0["addons"])
        }

    @Test
    fun BE4_addons_includedWhenChosen_ownOnly() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))
            addonDao.upsert(sampleAddon("https://example.com/p1/manifest.json", "p1.id", profileId = 1L))
            addonDao.upsert(
                sampleAddon("https://example.com/p2/manifest.json", "p2.id", profileId = 2L, shared = true),
            )

            val sink = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = true),
                sink,
            )

            val json = Json.parseToJsonElement(sink.toString(Charsets.UTF_8.name())).jsonObject
            val addonsArray = json["profiles"]!!.jsonArray[0].jsonObject["addons"]!!.jsonArray
            assertEquals(1, addonsArray.size)
            assertEquals("p1.id", addonsArray[0].jsonObject["id"]!!.jsonPrimitive.content)
            assertNull(addonsArray[0].jsonObject["shared"])
        }

    @Test
    fun BE5_neverContainsPinMaterial() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "LockedUser").copy(isLocked = true))

            val sink = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = true, includeAddons = true),
                sink,
            )

            val output = sink.toString(Charsets.UTF_8.name()).lowercase()
            assertFalse(output.contains("secrethash"))
            assertFalse(output.contains("\"pin\""))
            assertFalse(output.contains("\"hash\""))
            assertFalse(output.contains("\"salt\""))
            assertFalse(output.contains("\"iterations\""))
            assertFalse(output.contains("\"islocked\""))
            assertFalse(output.contains("\"locked\""))
        }

    @Test
    fun BE6_exportAll_includesEveryProfile_inRepositoryOrder() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value =
                listOf(testProfile(1L, "Alice"), testProfile(2L, "Bob"), testProfile(3L, "Charlie"))

            val sink = ByteArrayOutputStream()
            val result =
                createExporter().export(
                    ExportScope.All,
                    ExportOptions(includeSearchHistory = false, includeAddons = false),
                    sink,
                )

            assertTrue(result is ExportResult.Done)
            assertEquals(3, (result as ExportResult.Done).summary.profiles)

            val profiles =
                Json
                    .parseToJsonElement(sink.toString(Charsets.UTF_8.name()))
                    .jsonObject["profiles"]!!
                    .jsonArray
            assertEquals(3, profiles.size)
            assertEquals("Alice", profiles[0].jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("Bob", profiles[1].jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("Charlie", profiles[2].jsonObject["name"]!!.jsonPrimitive.content)
        }

    @Test
    fun BE7_header_hasFormatVersionAppVersionAndTime() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))

            val sink = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sink,
            )

            val json = Json.parseToJsonElement(sink.toString(Charsets.UTF_8.name())).jsonObject
            assertEquals(BACKUP_FORMAT, json["format"]!!.jsonPrimitive.content)
            assertEquals(BACKUP_VERSION.toString(), json["backupVersion"]!!.jsonPrimitive.content)
            assertEquals("1.2.3", json["appVersion"]!!.jsonPrimitive.content)
            assertEquals(fixedEpochMs.toString(), json["createdAt"]!!.jsonPrimitive.content)
        }

    @Test
    fun BE8_photo_includedWhenFilePresent_asBase64() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice").copy(avatarPhotoStamp = 12345L))
            val photoBytes = byteArrayOf(1, 2, 3)
            avatarPhotoStore.write(1L, photoBytes)

            val sink = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sink,
            )

            val json = Json.parseToJsonElement(sink.toString(Charsets.UTF_8.name())).jsonObject
            val avatarObj = json["profiles"]!!.jsonArray[0].jsonObject["avatar"]!!.jsonObject
            val base64 = avatarObj["photoJpegBase64"]!!.jsonPrimitive.content
            val decoded = Base64.getDecoder().decode(base64)
            assertTrue(photoBytes.contentEquals(decoded))
        }

    @Test
    fun BE9_photo_skippedWhenMissingOrTooLarge() =
        runTest(testDispatcher) {
            // Case A: file missing
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice").copy(avatarPhotoStamp = 12345L))
            val sinkMissing = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sinkMissing,
            )
            val jsonMissing = Json.parseToJsonElement(sinkMissing.toString(Charsets.UTF_8.name())).jsonObject
            assertNull(jsonMissing["profiles"]!!.jsonArray[0].jsonObject["avatar"]!!.jsonObject["photoJpegBase64"])

            // Case B: file of 512 KB + 1 byte
            avatarPhotoStore.write(1L, ByteArray(512 * 1024 + 1))
            val sinkTooLarge = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sinkTooLarge,
            )
            val jsonTooLarge = Json.parseToJsonElement(sinkTooLarge.toString(Charsets.UTF_8.name())).jsonObject
            assertNull(jsonTooLarge["profiles"]!!.jsonArray[0].jsonObject["avatar"]!!.jsonObject["photoJpegBase64"])
        }

    @Test
    fun BE10_profileNotFound_returnsNotFound_andWritesNothing() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = emptyList()

            val sink = ByteArrayOutputStream()
            val result =
                createExporter().export(
                    ExportScope.Profile(999L),
                    ExportOptions(includeSearchHistory = false, includeAddons = false),
                    sink,
                )

            assertEquals(ExportResult.NotFound, result)
            assertEquals(0, sink.size())
        }

    @Test
    fun BE11_summaryCounts_matchWrittenData() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))
            libraryDao.upsert(sampleLibrary("m1", true))
            progressDao.upsert(sampleProgress("k1", false))
            searchHistoryDao.upsert(SearchHistoryEntity("dune", 100L, 1L))
            addonDao.upsert(sampleAddon("https://example.com/manifest.json", "id1"))

            val sink = ByteArrayOutputStream()
            val result =
                createExporter().export(
                    ExportScope.Profile(1L),
                    ExportOptions(includeSearchHistory = true, includeAddons = true),
                    sink,
                )

            assertTrue(result is ExportResult.Done)
            val summary = (result as ExportResult.Done).summary
            assertEquals(1, summary.profiles)
            assertEquals(1, summary.libraryItems)
            assertEquals(1, summary.progressEntries)
            assertEquals(1, summary.searches)
            assertEquals(1, summary.addons)

            val p0 =
                Json
                    .parseToJsonElement(sink.toString(Charsets.UTF_8.name()))
                    .jsonObject["profiles"]!!
                    .jsonArray[0]
                    .jsonObject
            assertEquals(summary.libraryItems, p0["library"]!!.jsonArray.size)
            assertEquals(summary.progressEntries, p0["progress"]!!.jsonArray.size)
            assertEquals(summary.searches, p0["searchHistory"]!!.jsonArray.size)
            assertEquals(summary.addons, p0["addons"]!!.jsonArray.size)
        }

    @Test
    fun BE12_output_isValidJson() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))

            val sink = ByteArrayOutputStream()
            createExporter().export(
                ExportScope.Profile(1L),
                ExportOptions(includeSearchHistory = false, includeAddons = false),
                sink,
            )

            val json = Json.parseToJsonElement(sink.toString(Charsets.UTF_8.name())).jsonObject
            assertTrue(json.containsKey("format"))
            assertTrue(json.containsKey("backupVersion"))
            assertTrue(json.containsKey("appVersion"))
            assertTrue(json.containsKey("createdAt"))
            assertTrue(json.containsKey("profiles"))
        }

    @Test
    fun BE13_tooLarge_returnsTooLarge_andWritesNothing() =
        runTest(testDispatcher) {
            profileRepo.profilesFlow.value = listOf(testProfile(1L, "Alice"))

            val sink = ByteArrayOutputStream()
            val result =
                createExporter(maxBytes = 10L).export(
                    ExportScope.Profile(1L),
                    ExportOptions(includeSearchHistory = false, includeAddons = false),
                    sink,
                )

            assertEquals(ExportResult.TooLarge, result)
            assertEquals(0, sink.size())
        }
}
