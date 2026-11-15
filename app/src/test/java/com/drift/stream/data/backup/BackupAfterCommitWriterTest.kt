package com.drift.stream.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drift.stream.data.local.prefs.AppStateRepositoryImpl
import com.drift.stream.data.local.prefs.ContentPrefsRepositoryImpl
import com.drift.stream.data.local.prefs.SettingsRepositoryImpl
import com.drift.stream.data.local.prefs.TorrentPrefsRepositoryImpl
import com.drift.stream.data.profile.AvatarPhotoStore
import com.drift.stream.data.profile.PersonalStore
import com.drift.stream.data.profile.ProfileDataStores
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BackupAfterCommitWriterTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC)

    private lateinit var stores: ProfileDataStores
    private lateinit var writer: BackupAfterCommitWriter
    private lateinit var legacySettingsStore: DataStore<Preferences>

    @Before
    fun setUp() {
        val legacyFile = File(tmpFolder.root, "settings.preferences_pb")
        legacySettingsStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher),
                produceFile = { legacyFile },
            )
        stores =
            ProfileDataStores(
                fileFor = { name -> File(tmpFolder.root, "$name.preferences_pb") },
                newScope = { CoroutineScope(testDispatcher) },
                legacySettings = legacySettingsStore,
            )
        val dummyRepo = TestProfileRepository()
        val dummyAvatarPhotoStore = TestAvatarPhotoStore(tmpFolder.root)
        writer = BackupAfterCommitWriter(dummyRepo, stores, dummyAvatarPhotoStore, clock)
    }

    @Test
    fun BW1_replace_settingsStore_removesOnlyPersonalKeys_keepsDeviceWideKeys() =
        runTest {
            legacySettingsStore.edit { prefs ->
                prefs[AppStateRepositoryImpl.LAST_ACTIVE_PROFILE_ID_KEY] = 1L
                prefs[AppStateRepositoryImpl.ALWAYS_SHOW_PICKER_KEY] = true
                prefs[AppStateRepositoryImpl.DEFAULT_ADDONS_SEEDED_KEY] = true
                prefs[AppStateRepositoryImpl.DEFAULT_ADDONS_SEEDED_PROFILES_KEY] = setOf("1")
                prefs[TorrentPrefsRepositoryImpl.KEY_WIFI_ONLY_TORRENTS] = false
                prefs[TorrentPrefsRepositoryImpl.KEY_CACHE_LIMIT_GB] = 4
                prefs[TorrentPrefsRepositoryImpl.KEY_CLEAR_CACHE_ON_EXIT] = true

                prefs[SettingsRepositoryImpl.KEY_THEME_MODE] = "light"
                prefs[SettingsRepositoryImpl.KEY_AMOLED] = true
                prefs[ContentPrefsRepositoryImpl.KEY_FAMILY_MODE] = true
            }

            val outcome =
                DbOutcome(
                    settingsToReplace =
                        listOf(
                            1L to
                                mapOf(
                                    PersonalStore.SETTINGS.legacyFileName to
                                        mapOf("theme_mode" to "dark"),
                                ),
                        ),
                )
            writer.performWrites(outcome)

            val prefs = legacySettingsStore.data.first()
            assertEquals(1L, prefs[AppStateRepositoryImpl.LAST_ACTIVE_PROFILE_ID_KEY])
            assertEquals(true, prefs[AppStateRepositoryImpl.ALWAYS_SHOW_PICKER_KEY])
            assertEquals(true, prefs[AppStateRepositoryImpl.DEFAULT_ADDONS_SEEDED_KEY])
            assertEquals(setOf("1"), prefs[AppStateRepositoryImpl.DEFAULT_ADDONS_SEEDED_PROFILES_KEY])
            assertEquals(false, prefs[TorrentPrefsRepositoryImpl.KEY_WIFI_ONLY_TORRENTS])
            assertEquals(4, prefs[TorrentPrefsRepositoryImpl.KEY_CACHE_LIMIT_GB])
            assertEquals(true, prefs[TorrentPrefsRepositoryImpl.KEY_CLEAR_CACHE_ON_EXIT])

            assertNull(prefs[SettingsRepositoryImpl.KEY_AMOLED])
            assertNull(prefs[ContentPrefsRepositoryImpl.KEY_FAMILY_MODE])
            assertEquals("dark", prefs[SettingsRepositoryImpl.KEY_THEME_MODE])
        }

    @Test
    fun BW2_replace_settingsStore_isOneEdit() =
        runTest {
            val countingStore = EditCountingDataStore(legacySettingsStore)
            val customStores =
                ProfileDataStores(
                    fileFor = { name -> File(tmpFolder.root, "$name.preferences_pb") },
                    newScope = { CoroutineScope(testDispatcher) },
                    legacySettings = countingStore,
                )
            val testWriter =
                BackupAfterCommitWriter(
                    profileRepository = TestProfileRepository(),
                    profileDataStores = customStores,
                    avatarPhotoStore = TestAvatarPhotoStore(tmpFolder.root),
                    clock = clock,
                )

            countingStore.edit { it[SettingsRepositoryImpl.KEY_THEME_MODE] = "light" }
            val countBefore = countingStore.editCount

            val outcome =
                DbOutcome(
                    settingsToReplace =
                        listOf(
                            1L to
                                mapOf(
                                    PersonalStore.SETTINGS.legacyFileName to
                                        mapOf("theme_mode" to "dark"),
                                ),
                        ),
                )
            testWriter.performWrites(outcome)

            assertEquals(countBefore + 1, countingStore.editCount)
        }

    @Test
    fun BW3_replace_sourceMemoryAndHomeRows_removeOnlyOwnedKeys() =
        runTest {
            val smStore = stores.storeFor(PersonalStore.SOURCE_MEMORY, 1L)
            smStore.edit { prefs ->
                prefs[stringPreferencesKey("src.a")] = "old_a"
                prefs[stringPreferencesKey("src.b")] = "old_b"
                prefs[stringPreferencesKey("unrelated_x")] = "keep_x"
            }

            val hrStore = stores.storeFor(PersonalStore.HOME_ROWS, 1L)
            hrStore.edit { prefs ->
                prefs[stringPreferencesKey("home_row_config")] = "old_config"
                prefs[stringPreferencesKey("unrelated_y")] = "keep_y"
            }

            val outcome =
                DbOutcome(
                    settingsToReplace =
                        listOf(
                            1L to
                                mapOf(
                                    PersonalStore.SOURCE_MEMORY.legacyFileName to
                                        mapOf("src.a" to "new_a", "src.c" to "new_c"),
                                    PersonalStore.HOME_ROWS.legacyFileName to
                                        mapOf("home_row_config" to "new_config"),
                                ),
                        ),
                )
            writer.performWrites(outcome)

            val smPrefs = smStore.data.first()
            assertEquals("new_a", smPrefs[stringPreferencesKey("src.a")])
            assertEquals("new_c", smPrefs[stringPreferencesKey("src.c")])
            assertNull(smPrefs[stringPreferencesKey("src.b")])
            assertEquals("keep_x", smPrefs[stringPreferencesKey("unrelated_x")])

            val hrPrefs = hrStore.data.first()
            assertEquals("new_config", hrPrefs[stringPreferencesKey("home_row_config")])
            assertEquals("keep_y", hrPrefs[stringPreferencesKey("unrelated_y")])
        }

    @Test
    fun BW4_doubleValue_isWrittenAsDoubleKey() =
        runTest {
            val smStore = stores.storeFor(PersonalStore.SOURCE_MEMORY, 1L)
            val outcome =
                DbOutcome(
                    settingsToReplace =
                        listOf(
                            1L to
                                mapOf(
                                    PersonalStore.SOURCE_MEMORY.legacyFileName to
                                        mapOf("src.double" to 3.14159),
                                ),
                        ),
                )
            writer.performWrites(outcome)

            val smPrefs = smStore.data.first()
            assertEquals(3.14159, smPrefs[doublePreferencesKey("src.double")] ?: 0.0, 0.000001)

            val readableWithFloat =
                try {
                    val floatVal: Float? = smPrefs[floatPreferencesKey("src.double")]
                    floatVal != null
                } catch (_: ClassCastException) {
                    false
                }
            assertFalse(readableWithFloat)
        }

    @Test
    fun BW5_addAsNew_writesValues_withoutRemovingAnything() =
        runTest {
            val p2Store = stores.storeFor(PersonalStore.SETTINGS, 2L)
            p2Store.edit { prefs ->
                prefs[stringPreferencesKey("existing_key")] = "existing_val"
            }

            val outcome =
                DbOutcome(
                    settingsToAdd =
                        listOf(
                            2L to
                                mapOf(
                                    PersonalStore.SETTINGS.legacyFileName to
                                        mapOf("theme_mode" to "dark"),
                                ),
                        ),
                )
            writer.performWrites(outcome)

            val prefs = p2Store.data.first()
            assertEquals("dark", prefs[stringPreferencesKey("theme_mode")])
            assertEquals("existing_val", prefs[stringPreferencesKey("existing_key")])
        }

    @Test
    fun BW6_replace_nonOwnerProfile_appliesTheSameRules() =
        runTest {
            val p2Store = stores.storeFor(PersonalStore.SETTINGS, 2L)
            p2Store.edit { prefs ->
                prefs[stringPreferencesKey("unrelated_key")] = "keep_p2"
                prefs[SettingsRepositoryImpl.KEY_THEME_MODE] = "light"
                prefs[SettingsRepositoryImpl.KEY_AMOLED] = true
            }

            val outcome =
                DbOutcome(
                    settingsToReplace =
                        listOf(
                            2L to
                                mapOf(
                                    PersonalStore.SETTINGS.legacyFileName to
                                        mapOf("theme_mode" to "dark"),
                                ),
                        ),
                )
            writer.performWrites(outcome)

            val prefs = p2Store.data.first()
            assertEquals("keep_p2", prefs[stringPreferencesKey("unrelated_key")])
            assertNull(prefs[SettingsRepositoryImpl.KEY_AMOLED])
            assertEquals("dark", prefs[SettingsRepositoryImpl.KEY_THEME_MODE])
        }

    private class TestProfileRepository : ProfileRepository {
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
        ) = Unit

        override suspend fun delete(id: Long): DeleteResult = error("unused")
    }

    private class TestAvatarPhotoStore(
        private val rootDir: File,
    ) : AvatarPhotoStore {
        override fun fileOf(profileId: Long): File = File(rootDir, "avatar_$profileId.jpg")

        override suspend fun write(
            profileId: Long,
            bytes: ByteArray,
        ) = Unit

        override suspend fun delete(profileId: Long) = Unit
    }

    private class EditCountingDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> by delegate {
        var editCount = 0
            private set

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            editCount++
            return delegate.updateData(transform)
        }
    }
}
