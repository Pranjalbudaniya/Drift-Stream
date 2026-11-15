package com.drift.stream.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drift.stream.data.local.db.TransactionRunner
import com.drift.stream.data.local.db.dao.FakeAddonDao
import com.drift.stream.data.local.db.dao.FakeLibraryDao
import com.drift.stream.data.local.db.dao.FakeProgressDao
import com.drift.stream.data.local.db.dao.FakeSearchHistoryDao
import com.drift.stream.data.local.db.dao.LibraryDao
import com.drift.stream.data.local.db.dao.ProgressDao
import com.drift.stream.data.local.db.entity.AddonEntity
import com.drift.stream.data.local.db.entity.LibraryItemEntity
import com.drift.stream.data.local.db.entity.SearchHistoryEntity
import com.drift.stream.data.local.db.entity.WatchProgressEntity
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
import com.drift.stream.domain.backup.BACKUP_FORMAT
import com.drift.stream.domain.backup.BACKUP_VERSION
import com.drift.stream.domain.backup.BackupAddonItem
import com.drift.stream.domain.backup.BackupLibraryItem
import com.drift.stream.domain.backup.BackupProgressItem
import com.drift.stream.domain.backup.BackupSearchItem
import com.drift.stream.domain.backup.ImportMode
import com.drift.stream.domain.backup.ImportRequest
import com.drift.stream.domain.backup.ImportResult
import com.drift.stream.domain.backup.ImportWarning
import com.drift.stream.domain.backup.ValidatedAvatar
import com.drift.stream.domain.backup.ValidatedBackup
import com.drift.stream.domain.backup.ValidatedProfile
import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.Profile
import com.drift.stream.domain.model.ProfileDraft
import com.drift.stream.domain.repository.CreateResult
import com.drift.stream.domain.repository.DeleteResult
import com.drift.stream.domain.repository.ProfileRepository
import com.drift.stream.domain.repository.UpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BackupImporterTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC)

    private lateinit var profileRepo: TestProfileRepository
    private lateinit var libraryDao: FakeLibraryDao
    private lateinit var progressDao: FakeProgressDao
    private lateinit var searchHistoryDao: FakeSearchHistoryDao
    private lateinit var addonDao: FakeAddonDao
    private lateinit var stores: ProfileDataStores
    private lateinit var avatarPhotoStore: TestAvatarPhotoStore
    private lateinit var transactionRunner: TestTransactionRunner

    @Before
    fun setUp() {
        profileRepo = TestProfileRepository()
        libraryDao = FakeLibraryDao()
        progressDao = FakeProgressDao()
        searchHistoryDao = FakeSearchHistoryDao()
        addonDao = FakeAddonDao()
        stores = createStores()
        avatarPhotoStore = TestAvatarPhotoStore(tmpFolder.root)
        transactionRunner =
            TestTransactionRunner(
                onSnapshot = { snapshotState() },
                onRestore = { restoreState() },
            )
    }

    private var libSnapshot: Map<String, LibraryItemEntity> = emptyMap()
    private var progSnapshot: Map<String, WatchProgressEntity> = emptyMap()
    private var searchSnapshot: List<SearchHistoryEntity> = emptyList()
    private var profilesSnapshot: List<Profile> = emptyList()

    private fun snapshotState() {
        libSnapshot = libraryDao.items.value
        progSnapshot = progressDao.progressEntities.value
        searchSnapshot = searchHistoryDao.entries.value
        profilesSnapshot = profileRepo.profilesFlow.value
    }

    private fun restoreState() {
        libraryDao.items.value = libSnapshot
        progressDao.progressEntities.value = progSnapshot
        searchHistoryDao.entries.value = searchSnapshot
        profileRepo.profilesFlow.value = profilesSnapshot
    }

    private class TestTransactionRunner(
        private val onSnapshot: () -> Unit,
        private val onRestore: () -> Unit,
    ) : TransactionRunner {
        val callLog = mutableListOf<String>()

        override suspend fun <T> run(block: suspend () -> T): T {
            onSnapshot()
            callLog.add("tx_begin")
            return try {
                val res = block()
                callLog.add("tx_commit")
                res
            } catch (e: Exception) {
                callLog.add("tx_rollback")
                onRestore()
                throw e
            }
        }
    }

    private class TestProfileRepository : ProfileRepository {
        val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
        private var idCounter = 10L

        override val profiles: Flow<List<Profile>> = profilesFlow

        override suspend fun ensureDefaultProfile(): Profile = error("unused")

        override suspend fun get(id: Long): Profile? = profilesFlow.value.firstOrNull { it.id == id }

        override suspend fun create(draft: ProfileDraft): CreateResult {
            val id = idCounter++
            val p =
                Profile(
                    id = id,
                    name = draft.name,
                    avatarColor = draft.avatarColor,
                    avatarEmoji = draft.avatarEmoji,
                    avatarPhotoStamp = draft.avatarPhotoStamp,
                    isOwner = false,
                    needsSetup = false,
                    createdAt = 1000L,
                    lastUsedAt = 1000L,
                    sortOrder = id.toInt(),
                )
            profilesFlow.value = profilesFlow.value + p
            return CreateResult.Created(p)
        }

        override suspend fun update(profile: Profile): UpdateResult = error("unused")

        override suspend fun completeSetup(
            id: Long,
            draft: ProfileDraft,
        ): UpdateResult = error("unused")

        override suspend fun setAvatarPhoto(
            profileId: Long,
            stamp: Long?,
        ) {
            val list = profilesFlow.value.toMutableList()
            val idx = list.indexOfFirst { it.id == profileId }
            if (idx != -1) {
                list[idx] = list[idx].copy(avatarPhotoStamp = stamp)
                profilesFlow.value = list
            }
        }

        override suspend fun delete(id: Long): DeleteResult = error("unused")
    }

    private class TestAvatarPhotoStore(
        private val rootDir: File,
    ) : AvatarPhotoStore {
        var throwOnWrite = false
        val written = mutableMapOf<Long, ByteArray>()

        override fun fileOf(profileId: Long): File = File(rootDir, "avatar_$profileId.jpg")

        override suspend fun write(
            profileId: Long,
            bytes: ByteArray,
        ) {
            if (throwOnWrite) throw IOException("Disk full")
            written[profileId] = bytes
            fileOf(profileId).writeBytes(bytes)
        }

        override suspend fun delete(profileId: Long) {
            written.remove(profileId)
            fileOf(profileId).delete()
        }
    }

    private fun createStores(failOnEdit: Boolean = false): ProfileDataStores {
        val legacyFile = File(tmpFolder.root, "settings.preferences_pb")
        val legacySettings: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher),
                produceFile = { legacyFile },
            )
        return ProfileDataStores(
            fileFor = { name ->
                if (failOnEdit) {
                    File("/dev/null/impossible/$name")
                } else {
                    File(tmpFolder.root, "$name.preferences_pb")
                }
            },
            newScope = { CoroutineScope(testDispatcher) },
            legacySettings = legacySettings,
        )
    }

    private fun createImporter(
        customStores: ProfileDataStores? = null,
        customDaos: BackupDaos? = null,
        customRunner: TransactionRunner? = null,
    ): DefaultBackupImporter {
        val st = customStores ?: stores
        val writer = BackupAfterCommitWriter(profileRepo, st, avatarPhotoStore, clock)
        return DefaultBackupImporter(
            profileRepository = profileRepo,
            daos = customDaos ?: BackupDaos(libraryDao, progressDao, searchHistoryDao, addonDao),
            transactionRunner = customRunner ?: transactionRunner,
            afterCommitWriter = writer,
            ioDispatcher = testDispatcher,
        )
    }

    private fun sampleLib(
        metaId: String = "m1",
        addedAt: Long = 100L,
    ) = BackupLibraryItem(
        metaId = metaId,
        type = "movie",
        name = "Movie $metaId",
        inWatchlist = true,
        addedAt = addedAt,
        listStatus = "PLAN_TO_WATCH",
    )

    private fun sampleProg(
        metaId: String = "m1",
        updatedAt: Long = 100L,
    ) = BackupProgressItem(
        key = "$metaId:$metaId",
        metaId = metaId,
        videoId = metaId,
        type = "movie",
        positionMs = 10,
        durationMs = 100,
        watched = false,
        updatedAt = updatedAt,
    )

    private fun sampleAddon(
        url: String = "https://x/manifest.json",
        pos: Int = 0,
    ) = BackupAddonItem(
        transportUrl = url,
        id = "a",
        name = "A",
        version = "1.0",
        manifestJson = "{}",
        enabled = true,
        position = pos,
        source = AddonSource.LOCAL.name,
        installedAt = 100L,
    )

    private fun sampleBackupProfile(
        name: String = "Alice",
        hasPhoto: Boolean = false,
        library: List<BackupLibraryItem> = emptyList(),
        progress: List<BackupProgressItem> = emptyList(),
        settings: Map<String, Map<String, Any>> = emptyMap(),
    ): ValidatedProfile =
        ValidatedProfile(
            name = name,
            avatar =
                ValidatedAvatar(
                    colorIndex = 1,
                    emoji = null,
                    photoJpegBytes =
                        if (hasPhoto) {
                            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
                        } else {
                            null
                        },
                ),
            library = library,
            progress = progress,
            searchHistory = null,
            addons = null,
            settings = settings,
        )

    private fun sampleBackup(
        vararg profiles: ValidatedProfile,
        skipped: Int = 0,
    ): ValidatedBackup =
        ValidatedBackup(
            format = BACKUP_FORMAT,
            backupVersion = BACKUP_VERSION,
            appVersion = "1.0.0",
            createdAt = 1000L,
            profiles = profiles.toList(),
            skipped = skipped,
        )

    @Test
    fun IM1_addAsNew_createsUnlockedNonOwnerProfile_withRows() =
        runTest {
            val lib = listOf(sampleLib("tt1", 100L), sampleLib("tt2", 101L))
            val prog = listOf(sampleProg("tt1", 200L))
            val search = listOf(BackupSearchItem("dune", 300L))
            val p =
                sampleBackupProfile(
                    name = "Alice",
                    library = lib,
                    progress = prog,
                ).copy(searchHistory = search)
            val backup = sampleBackup(p)

            val req = ImportRequest(ImportMode.AddAsNew, listOf(0), includeAddons = false)
            val result = createImporter().import(backup, req)

            assertTrue(result is ImportResult.Done)
            val done = result as ImportResult.Done
            assertEquals(1, done.summary.profilesCreated)
            assertEquals(2, done.summary.libraryItems)
            assertEquals(1, done.summary.progressEntries)
            assertEquals(1, done.summary.searches)

            val created = profileRepo.profiles.first().first()
            assertEquals("Alice", created.name)
            assertFalse(created.isOwner)
            assertFalse(created.needsSetup)
            assertEquals(2, libraryDao.getAllForProfile(created.id).size)
            assertEquals(1, progressDao.getAllForProfile(created.id).size)
            assertEquals(1, searchHistoryDao.getAll(created.id).size)
        }

    @Test
    fun IM2_addAsNew_duplicateName_getsNumberedName() =
        runTest {
            val importer = createImporter()
            profileRepo.create(ProfileDraft(name = "Asha", avatarColor = 0))
            val p = sampleBackupProfile(name = "Asha")
            val backup = sampleBackup(p)

            val res1 = importer.import(backup, ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res1 is ImportResult.Done)
            val created1 = profileRepo.profiles.first().last()
            assertEquals("Asha (2)", created1.name)

            val res2 = importer.import(backup, ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res2 is ImportResult.Done)
            val created2 = profileRepo.profiles.first().last()
            assertEquals("Asha (3)", created2.name)

            val name20 = "a".repeat(20)
            profileRepo.create(ProfileDraft(name = name20, avatarColor = 0))
            val p20 = sampleBackupProfile(name = name20)
            importer.import(sampleBackup(p20), ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            val created3 = profileRepo.profiles.first().last()
            assertTrue(created3.name.length <= 20)
            assertTrue(created3.name.endsWith(" (2)"))
        }

    @Test
    fun IM3_addAsNew_multipleSelected_allCreatedInFileOrder() =
        runTest {
            val p0 = sampleBackupProfile(name = "P0")
            val p1 = sampleBackupProfile(name = "P1")
            val p2 = sampleBackupProfile(name = "P2")
            val backup = sampleBackup(p0, p1, p2)

            val res = createImporter().import(backup, ImportRequest(ImportMode.AddAsNew, listOf(0, 2), false))
            assertTrue(res is ImportResult.Done)

            val profiles = profileRepo.profiles.first()
            assertEquals(2, profiles.size)
            assertEquals("P0", profiles[0].name)
            assertEquals("P2", profiles[1].name)
        }

    @Test
    fun IM4_addAsNew_dbFailure_createsNothing() =
        runTest {
            val failingProgressDao =
                object : ProgressDao by progressDao {
                    override suspend fun upsertAll(entities: List<WatchProgressEntity>): Unit =
                        error("Database disk I/O error")
                }
            val importer =
                createImporter(
                    customDaos = BackupDaos(libraryDao, failingProgressDao, searchHistoryDao, addonDao),
                )
            val p =
                sampleBackupProfile(
                    name = "Bob",
                    library = listOf(sampleLib("m1")),
                    progress = listOf(sampleProg("m1")),
                )

            val res = importer.import(sampleBackup(p), ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res is ImportResult.Failed)
            assertTrue(profileRepo.profiles.first().isEmpty())
            assertTrue(libraryDao.items.value.isEmpty())
        }

    @Test
    fun IM5_addAsNew_settings_writtenAfterCommit_toNewProfileStores() =
        runTest {
            val order = mutableListOf<String>()
            val trackingRunner =
                object : TransactionRunner {
                    override suspend fun <T> run(block: suspend () -> T): T {
                        order.add("tx_start")
                        val r = block()
                        order.add("tx_end")
                        return r
                    }
                }
            val settingsData = mapOf(PersonalStore.SETTINGS.legacyFileName to mapOf("theme_mode" to "dark"))
            val p = sampleBackupProfile(name = "Asha", settings = settingsData)

            val importer = createImporter(customRunner = trackingRunner)
            val res = importer.import(sampleBackup(p), ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res is ImportResult.Done)

            val createdId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            val storeData = stores.storeFor(PersonalStore.SETTINGS, createdId).data.first()
            val writtenTheme = storeData[stringPreferencesKey("theme_mode")]
            assertEquals("dark", writtenTheme)
            assertEquals(listOf("tx_start", "tx_end"), order)
        }

    @Test
    fun IM6_addAsNew_photo_writtenWhenValid_skippedWhenInvalid() =
        runTest {
            val importer = createImporter()
            val validP = sampleBackupProfile(name = "ValidPhoto", hasPhoto = true)
            val res1 = importer.import(sampleBackup(validP), ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res1 is ImportResult.Done)
            val p1 = profileRepo.profiles.first().first()
            assertNotNull(p1.avatarPhotoStamp)
            assertNotNull(avatarPhotoStore.written[p1.id])

            val invalidP = sampleBackupProfile(name = "NoPhoto", hasPhoto = false)
            val res2 = importer.import(sampleBackup(invalidP), ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res2 is ImportResult.Done)
            val p2 = profileRepo.profiles.first().last()
            assertNull(p2.avatarPhotoStamp)

            avatarPhotoStore.throwOnWrite = true
            val res3 = importer.import(sampleBackup(validP), ImportRequest(ImportMode.AddAsNew, listOf(0), false))
            assertTrue(res3 is ImportResult.Done)
            assertEquals(listOf(ImportWarning.PhotoNotSaved), (res3 as ImportResult.Done).summary.warnings)
        }

    @Test
    fun IM7_mergeInto_unionsData_keepsNameAvatarSettingsAndPin() =
        runTest {
            profileRepo.create(ProfileDraft(name = "Target", avatarColor = 5, avatarEmoji = "⭐"))
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            stores.storeFor(PersonalStore.SETTINGS, targetId).edit {
                it[stringPreferencesKey("theme_mode")] = "light"
            }
            libraryDao.upsert(
                LibraryItemEntity("m1", "movie", "Old M1", null, null, true, 100L, "PLAN_TO_WATCH", targetId),
            )

            val fileLib = listOf(sampleLib("m1", 200L), sampleLib("m2", 150L))
            val pSettings = mapOf(PersonalStore.SETTINGS.legacyFileName to mapOf("theme_mode" to "dark"))
            val p = sampleBackupProfile(name = "FromFile", library = fileLib, settings = pSettings)

            val req = ImportRequest(ImportMode.MergeInto(targetId), listOf(0), false)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)

            val target = profileRepo.get(targetId)!!
            assertEquals("Target", target.name)
            assertEquals(5, target.avatarColor)
            assertEquals("⭐", target.avatarEmoji)
            val themeStore = stores.storeFor(PersonalStore.SETTINGS, targetId)
            val theme = themeStore.data.first()[stringPreferencesKey("theme_mode")]
            assertEquals("light", theme)

            val localItems = libraryDao.getAllForProfile(targetId).associateBy { it.metaId }
            assertEquals(2, localItems.size)
            assertEquals(200L, localItems["m1"]?.addedAt)
            assertEquals(150L, localItems["m2"]?.addedAt)
        }

    @Test
    fun IM8_replaceIn_replacesLibraryProgressSearch_keepsProfileRow() =
        runTest {
            profileRepo.create(ProfileDraft(name = "Target", avatarColor = 3))
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            libraryDao.upsert(
                LibraryItemEntity("old_m", "movie", "Old", null, null, true, 10L, "PLAN_TO_WATCH", targetId),
            )
            progressDao.upsert(
                WatchProgressEntity("old_k", "old_m", "v", "movie", null, null, 1, 2, false, 10L, targetId),
            )
            searchHistoryDao.upsert(SearchHistoryEntity("old_q", 10L, targetId))

            val newLib = listOf(sampleLib("new_m", 20L))
            val newProg = listOf(sampleProg("new_m", 20L))
            val newSearch = listOf(BackupSearchItem("new_q", 20L))
            val p =
                sampleBackupProfile(
                    name = "FileProfile",
                    library = newLib,
                    progress = newProg,
                ).copy(searchHistory = newSearch)

            val req = ImportRequest(ImportMode.ReplaceIn(targetId), listOf(0), false)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)

            assertEquals(listOf("new_m"), libraryDao.getAllForProfile(targetId).map { it.metaId })
            val progKeys = progressDao.getAllForProfile(targetId).map { it.key }
            assertEquals(listOf("new_m:new_m"), progKeys)
            assertEquals(listOf("new_q"), searchHistoryDao.getAll(targetId).map { it.query })
            assertEquals("Target", profileRepo.get(targetId)?.name)
        }

    @Test
    fun IM9_replaceIn_addonsAndSearchNull_keepLocal() =
        runTest {
            profileRepo.create(ProfileDraft(name = "Target", avatarColor = 0))
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            searchHistoryDao.upsert(SearchHistoryEntity("local_query", 100L, targetId))
            val localAddon =
                AddonEntity(
                    transportUrl = "https://local/manifest.json",
                    id = "loc",
                    name = "L",
                    version = "1.0",
                    manifestJson = "{}",
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 100L,
                    profileId = targetId,
                    shared = false,
                )
            addonDao.upsert(localAddon)

            val p = sampleBackupProfile(name = "P")
            val req = ImportRequest(ImportMode.ReplaceIn(targetId), listOf(0), includeAddons = true)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)

            assertEquals(1, searchHistoryDao.getAll(targetId).size)
            assertEquals(1, addonDao.getAllForProfile(targetId).size)
        }

    @Test
    fun IM10_replaceIn_addonsIncluded_replacesOwnAddons() =
        runTest {
            profileRepo.create(ProfileDraft(name = "Target", avatarColor = 0))
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            val oldAddon =
                AddonEntity(
                    transportUrl = "https://old/manifest.json",
                    id = "old",
                    name = "O",
                    version = "1.0",
                    manifestJson = "{}",
                    enabled = true,
                    position = 0,
                    source = AddonSource.LOCAL,
                    installedAt = 100L,
                    profileId = targetId,
                    shared = false,
                )
            addonDao.upsert(oldAddon)

            val newAddon = sampleAddon("https://new/manifest.json", 5)
            val p = sampleBackupProfile(name = "P").copy(addons = listOf(newAddon))

            val req = ImportRequest(ImportMode.ReplaceIn(targetId), listOf(0), includeAddons = true)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)

            val local = addonDao.getAllForProfile(targetId)
            assertEquals(1, local.size)
            assertEquals("https://new/manifest.json", local[0].transportUrl)
            assertEquals(5, local[0].position)
        }

    @Test
    fun IM11_replaceIn_settingsStores_replacedFromFile() =
        runTest {
            profileRepo.create(ProfileDraft(name = "Target", avatarColor = 0))
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            stores.storeFor(PersonalStore.SETTINGS, targetId).edit {
                it[stringPreferencesKey("app_font")] = "old_val"
            }
            stores.storeFor(PersonalStore.SOURCE_MEMORY, targetId).edit {
                it[stringPreferencesKey("src.1")] = "keep_me"
            }

            val pSettings = mapOf(PersonalStore.SETTINGS.legacyFileName to mapOf("theme_mode" to "dark"))
            val p = sampleBackupProfile(name = "P", settings = pSettings)

            val req = ImportRequest(ImportMode.ReplaceIn(targetId), listOf(0), false)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)

            val settingsPrefs = stores.storeFor(PersonalStore.SETTINGS, targetId).data.first()
            assertNull(settingsPrefs[stringPreferencesKey("app_font")])
            assertEquals("dark", settingsPrefs[stringPreferencesKey("theme_mode")])

            val srcPrefs = stores.storeFor(PersonalStore.SOURCE_MEMORY, targetId).data.first()
            assertEquals("keep_me", srcPrefs[stringPreferencesKey("src.1")])
        }

    @Test
    fun IM12_replaceIn_dbFailure_changesNothing() =
        runTest {
            profileRepo.create(ProfileDraft(name = "Target", avatarColor = 0))
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            val localItem =
                LibraryItemEntity(
                    metaId = "local_m",
                    type = "movie",
                    name = "Local",
                    poster = null,
                    background = null,
                    inWatchlist = true,
                    addedAt = 100L,
                    listStatus = "PLAN_TO_WATCH",
                    profileId = targetId,
                )
            libraryDao.upsert(localItem)

            val failOnUpsert = true
            val failingLibraryDao =
                object : LibraryDao by libraryDao {
                    override suspend fun upsertAll(entities: List<LibraryItemEntity>) {
                        if (failOnUpsert) error("Failure after delete")
                        libraryDao.upsertAll(entities)
                    }
                }

            val importer =
                createImporter(
                    customDaos = BackupDaos(failingLibraryDao, progressDao, searchHistoryDao, addonDao),
                )
            val p =
                sampleBackupProfile(
                    name = "P",
                    library = listOf(sampleLib("new_m", 200L)),
                )

            val req = ImportRequest(ImportMode.ReplaceIn(targetId), listOf(0), false)
            val res = importer.import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Failed)
            assertEquals(1, failingLibraryDao.getAllForProfile(targetId).size)
            assertEquals("local_m", failingLibraryDao.getAllForProfile(targetId)[0].metaId)
        }

    @Test
    fun IM13_includeAddonsFalse_importsNoAddons() =
        runTest {
            val p = sampleBackupProfile(name = "P").copy(addons = listOf(sampleAddon()))

            val req = ImportRequest(ImportMode.AddAsNew, listOf(0), includeAddons = false)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)
            val targetId =
                profileRepo.profiles
                    .first()
                    .first()
                    .id
            assertTrue(addonDao.getAllForProfile(targetId).isEmpty())
        }

    @Test
    fun IM14_neverImportsOwnerFlagPinOrLock() =
        runTest {
            val p = sampleBackupProfile(name = "Newbie")
            val req = ImportRequest(ImportMode.AddAsNew, listOf(0), false)
            val res = createImporter().import(sampleBackup(p), req)
            assertTrue(res is ImportResult.Done)

            val created = profileRepo.profiles.first().first()
            assertFalse(created.isOwner)
            assertFalse(created.needsSetup)
        }

    @Test
    fun IM15_targetNotFound_returnsTargetNotFound_noWrites() =
        runTest {
            val p = sampleBackupProfile(name = "P")
            val req = ImportRequest(ImportMode.MergeInto(999L), listOf(0), false)
            val res = createImporter().import(sampleBackup(p), req)
            assertEquals(ImportResult.TargetNotFound, res)
            assertTrue(profileRepo.profiles.first().isEmpty())
        }

    @Test
    fun IM16_summary_countsImportedAndSkipped() =
        runTest {
            val p =
                sampleBackupProfile(
                    name = "P",
                    library = listOf(sampleLib("m1")),
                    progress = listOf(sampleProg("m1")),
                ).copy(
                    searchHistory = listOf(BackupSearchItem("q", 1L)),
                    addons = listOf(sampleAddon()),
                )
            val backup = sampleBackup(p, skipped = 7)

            val req = ImportRequest(ImportMode.AddAsNew, listOf(0), includeAddons = true)
            val res = createImporter().import(backup, req)
            assertTrue(res is ImportResult.Done)
            val summary = (res as ImportResult.Done).summary
            assertEquals(1, summary.profilesCreated)
            assertEquals(1, summary.libraryItems)
            assertEquals(1, summary.progressEntries)
            assertEquals(1, summary.searches)
            assertEquals(1, summary.addons)
            assertEquals(7, summary.skipped)
        }

    @Test
    fun IM17_dataStoreFailure_afterCommit_warnsAndKeepsDbData() =
        runTest {
            val failingStores = createStores(failOnEdit = true)
            val settings = mapOf(PersonalStore.SETTINGS.legacyFileName to mapOf("theme_mode" to "dark"))
            val p =
                sampleBackupProfile(
                    name = "P",
                    library = listOf(sampleLib("m1")),
                    settings = settings,
                )

            val importer = createImporter(customStores = failingStores)
            val req = ImportRequest(ImportMode.AddAsNew, listOf(0), false)
            val res = importer.import(sampleBackup(p), req)

            assertTrue(res is ImportResult.Done)
            val summary = (res as ImportResult.Done).summary
            assertTrue(summary.warnings.contains(ImportWarning.SettingsNotFullySaved))
            val created = profileRepo.profiles.first().first()
            assertEquals(1, libraryDao.getAllForProfile(created.id).size)
        }
}
