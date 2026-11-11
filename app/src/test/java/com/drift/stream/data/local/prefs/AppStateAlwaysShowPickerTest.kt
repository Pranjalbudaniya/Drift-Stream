package com.drift.stream.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppStateAlwaysShowPickerTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createRepository(
        testScope: TestScope,
        fileName: String,
    ): AppStateRepositoryImpl {
        val testFile = File(tmpFolder.root, fileName)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { testFile },
            )
        return AppStateRepositoryImpl(dataStore)
    }

    @Test
    fun AS1_alwaysShowPicker_defaultsFalse() =
        runTest {
            val repo = createRepository(this, "fresh_always_show_picker.preferences_pb")
            assertFalse(repo.alwaysShowPicker.first())
        }

    @Test
    fun AS2_alwaysShowPicker_roundTrip() =
        runTest {
            val repo = createRepository(this, "roundtrip_always_show_picker.preferences_pb")
            assertFalse(repo.alwaysShowPicker.first())

            repo.setAlwaysShowPicker(true)
            assertTrue(repo.alwaysShowPicker.first())

            repo.setAlwaysShowPicker(false)
            assertFalse(repo.alwaysShowPicker.first())
        }
}
