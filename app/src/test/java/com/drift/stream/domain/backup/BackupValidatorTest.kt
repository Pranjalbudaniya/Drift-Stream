package com.drift.stream.domain.backup

import com.drift.stream.domain.model.AddonSource
import com.drift.stream.domain.model.ListStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BackupValidatorTest {
    private fun sampleProfile(
        name: String = "Alice",
        avatar: BackupAvatar = BackupAvatar(colorIndex = 0),
        library: List<BackupLibraryItem> = emptyList(),
        progress: List<BackupProgressItem> = emptyList(),
        searchHistory: List<BackupSearchItem>? = null,
    ): BackupProfile =
        BackupProfile(
            name = name,
            avatar = avatar,
            library = library,
            progress = progress,
            searchHistory = searchHistory,
            settings = emptyList(),
            addons = null,
        )

    private fun sampleFile(
        format: String = BACKUP_FORMAT,
        backupVersion: Int = BACKUP_VERSION,
        appVersion: String = "1.0.0",
        createdAt: Long = 1000L,
        profiles: List<BackupProfile> = listOf(sampleProfile()),
    ): BackupFile =
        BackupFile(
            format = format,
            backupVersion = backupVersion,
            appVersion = appVersion,
            createdAt = createdAt,
            profiles = profiles,
        )

    private fun encode(file: BackupFile): ByteArray =
        Json.encodeToString(BackupFile.serializer(), file).encodeToByteArray()

    @Test
    fun VA1_wrongFormat_isNotABackup() {
        val badFormatFile = sampleFile(format = "other")
        val outcome1 = BackupParser.parse(encode(badFormatFile))
        assertEquals(ParseOutcome.Rejected(RejectReason.NotABackup), outcome1)

        val plainTextBytes = "hello".encodeToByteArray()
        val outcome2 = BackupParser.parse(plainTextBytes)
        assertEquals(ParseOutcome.Rejected(RejectReason.Corrupt), outcome2)
    }

    @Test
    fun VA2_newerVersion_isRejected() {
        val outcome = BackupParser.parse(encode(sampleFile(backupVersion = 2)))
        assertEquals(ParseOutcome.Rejected(RejectReason.NewerVersion), outcome)
    }

    @Test
    fun VA3_versionZeroOrNegative_isCorrupt() {
        val outcome0 = BackupParser.parse(encode(sampleFile(backupVersion = 0)))
        assertEquals(ParseOutcome.Rejected(RejectReason.Corrupt), outcome0)

        val outcomeNeg = BackupParser.parse(encode(sampleFile(backupVersion = -1)))
        assertEquals(ParseOutcome.Rejected(RejectReason.Corrupt), outcomeNeg)
    }

    @Test
    fun VA4_noProfiles_isRejected() {
        val outcome = BackupParser.parse(encode(sampleFile(profiles = emptyList())))
        assertEquals(ParseOutcome.Rejected(RejectReason.NoProfiles), outcome)
    }

    @Test
    fun VA5_profileName_isCleaned() {
        val p1 = sampleProfile(name = "  A\u0007sha  ")
        val outcome1 = BackupParser.parse(encode(sampleFile(profiles = listOf(p1))))
        assertTrue(outcome1 is ParseOutcome.Ready)
        assertEquals("Asha", (outcome1 as ParseOutcome.Ready).backup.profiles[0].name)

        val longName = "a".repeat(30)
        val p2 = sampleProfile(name = longName)
        val outcome2 = BackupParser.parse(encode(sampleFile(profiles = listOf(p2))))
        assertTrue(outcome2 is ParseOutcome.Ready)
        assertEquals("a".repeat(20), (outcome2 as ParseOutcome.Ready).backup.profiles[0].name)
    }

    @Test
    fun VA6_blankProfileName_getsFallback() {
        val p = sampleProfile(name = "   ")
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        assertEquals("Imported profile", (outcome as ParseOutcome.Ready).backup.profiles[0].name)
    }

    @Test
    fun VA7_libraryItem_blankMetaId_isSkipped() {
        val item =
            BackupLibraryItem(
                metaId = "",
                type = "movie",
                name = "Test Movie",
                inWatchlist = true,
                addedAt = 1000L,
                listStatus = ListStatus.PLAN_TO_WATCH.name,
            )
        val p = sampleProfile(library = listOf(item))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .library.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA8_libraryItem_unknownListStatus_isSkipped() {
        val item =
            BackupLibraryItem(
                metaId = "m1",
                type = "movie",
                name = "Test Movie",
                inWatchlist = true,
                addedAt = 1000L,
                listStatus = "NOPE",
            )
        val p = sampleProfile(library = listOf(item))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .library.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA9_libraryItem_badImageUrl_dropsOnlyTheImage() {
        val itemBad =
            BackupLibraryItem(
                metaId = "m1",
                type = "movie",
                name = "Bad Poster",
                poster = "javascript:x",
                inWatchlist = true,
                addedAt = 1000L,
                listStatus = ListStatus.PLAN_TO_WATCH.name,
            )
        val itemGood =
            BackupLibraryItem(
                metaId = "m2",
                type = "movie",
                name = "Good Poster",
                poster = "https://a/b.jpg",
                inWatchlist = true,
                addedAt = 1000L,
                listStatus = ListStatus.PLAN_TO_WATCH.name,
            )
        val p = sampleProfile(library = listOf(itemBad, itemGood))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            2,
            ready.backup.profiles[0]
                .library.size,
        )
        assertNull(
            ready.backup.profiles[0]
                .library[0]
                .poster,
        )
        assertEquals(
            "https://a/b.jpg",
            ready.backup.profiles[0]
                .library[1]
                .poster,
        )
        assertEquals(0, ready.skipped)
    }

    @Test
    fun VA10_progress_negativeValues_areSkipped() {
        val item =
            BackupProgressItem(
                key = "k1",
                metaId = "m1",
                videoId = "v1",
                type = "movie",
                positionMs = -1L,
                durationMs = 1000L,
                watched = false,
                updatedAt = 1000L,
            )
        val p = sampleProfile(progress = listOf(item))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .progress.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA11_progress_positionAboveDuration_isClamped() {
        val item =
            BackupProgressItem(
                key = "k1",
                metaId = "m1",
                videoId = "v1",
                type = "movie",
                positionMs = 2000L,
                durationMs = 1000L,
                watched = false,
                updatedAt = 1000L,
            )
        val p = sampleProfile(progress = listOf(item))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            1,
            ready.backup.profiles[0]
                .progress.size,
        )
        assertEquals(
            1000L,
            ready.backup.profiles[0]
                .progress[0]
                .positionMs,
        )
        assertEquals(0, ready.skipped)
    }

    @Test
    fun VA12_search_blankQuery_isSkipped() {
        val item = BackupSearchItem(query = "  ", lastUsedAt = 1000L)
        val p = sampleProfile(searchHistory = listOf(item))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .searchHistory
                ?.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA13_addon_nonHttpUrl_isSkipped() {
        val bad1 =
            BackupAddonItem(
                transportUrl = "file:///x",
                id = "a1",
                name = "A1",
                version = "1.0",
                manifestJson = "{}",
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL.name,
                installedAt = 1000L,
            )
        val bad2 = bad1.copy(transportUrl = "javascript:x")
        val bad3 = bad1.copy(transportUrl = "ftp://x")
        val good = bad1.copy(transportUrl = "https://x/manifest.json")
        val p = sampleProfile().copy(addons = listOf(bad1, bad2, bad3, good))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            1,
            ready.backup.profiles[0]
                .addons
                ?.size,
        )
        assertEquals(
            "https://x/manifest.json",
            ready.backup.profiles[0]
                .addons
                ?.get(0)
                ?.transportUrl,
        )
        assertEquals(3, ready.skipped)
    }

    @Test
    fun VA14_addon_manifestNotJsonObject_isSkipped() {
        val bad1 =
            BackupAddonItem(
                transportUrl = "https://x1/manifest.json",
                id = "a1",
                name = "A1",
                version = "1.0",
                manifestJson = "[1,2]",
                enabled = true,
                position = 0,
                source = AddonSource.LOCAL.name,
                installedAt = 1000L,
            )
        val bad2 = bad1.copy(transportUrl = "https://x2/manifest.json", manifestJson = "nope")
        val good = bad1.copy(transportUrl = "https://x3/manifest.json", manifestJson = "{}")
        val p = sampleProfile().copy(addons = listOf(bad1, bad2, good))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            1,
            ready.backup.profiles[0]
                .addons
                ?.size,
        )
        assertEquals(
            "https://x3/manifest.json",
            ready.backup.profiles[0]
                .addons
                ?.get(0)
                ?.transportUrl,
        )
        assertEquals(2, ready.skipped)
    }

    @Test
    fun VA15_addon_unknownSource_isSkipped() {
        val item =
            BackupAddonItem(
                transportUrl = "https://x/manifest.json",
                id = "a1",
                name = "A1",
                version = "1.0",
                manifestJson = "{}",
                enabled = true,
                position = 0,
                source = "WEIRD",
                installedAt = 1000L,
            )
        val p = sampleProfile().copy(addons = listOf(item))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .addons
                ?.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA16_settings_unknownKeyInSettingsStore_isSkipped() {
        val e1 = BackupPrefEntry("last_active_profile_id", "long", JsonPrimitive(1L))
        val e2 = BackupPrefEntry("always_show_picker", "boolean", JsonPrimitive(true))
        val store = BackupSettingsStore(store = "settings", entries = listOf(e1, e2))
        val p = sampleProfile().copy(settings = listOf(store))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .settings["settings"]
                ?.size,
        )
        assertEquals(2, ready.skipped)
    }

    @Test
    fun VA17_settings_typeMismatch_isSkipped() {
        val e = BackupPrefEntry("theme_mode", "int", JsonPrimitive(1))
        val store = BackupSettingsStore(store = "settings", entries = listOf(e))
        val p = sampleProfile().copy(settings = listOf(store))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .settings["settings"]
                ?.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA18_settings_valueDoesNotMatchTag_isSkipped() {
        val e = BackupPrefEntry("seek_step_seconds", "int", JsonPrimitive("x"))
        val store = BackupSettingsStore(store = "settings", entries = listOf(e))
        val p = sampleProfile().copy(settings = listOf(store))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            0,
            ready.backup.profiles[0]
                .settings["settings"]
                ?.size,
        )
        assertEquals(1, ready.skipped)
    }

    @Test
    fun VA19_photo_notJpegOrTooLarge_isDropped() {
        val pngBytes = "PNG..otherdata".encodeToByteArray()
        val pPng =
            sampleProfile(
                avatar = BackupAvatar(colorIndex = 0, photoJpegBase64 = Base64.getEncoder().encodeToString(pngBytes)),
            )
        val outcomePng = BackupParser.parse(encode(sampleFile(profiles = listOf(pPng))))
        assertTrue(outcomePng is ParseOutcome.Ready)
        assertNull(
            (outcomePng as ParseOutcome.Ready)
                .backup.profiles[0]
                .avatar.photoJpegBytes,
        )

        val tooLargeBytes = ByteArray(512 * 1024 + 1) { 0xFF.toByte() }
        tooLargeBytes[0] = 0xFF.toByte()
        tooLargeBytes[1] = 0xD8.toByte()
        tooLargeBytes[2] = 0xFF.toByte()
        val pLarge =
            sampleProfile(
                avatar =
                    BackupAvatar(
                        colorIndex = 0,
                        photoJpegBase64 = Base64.getEncoder().encodeToString(tooLargeBytes),
                    ),
            )
        val outcomeLarge = BackupParser.parse(encode(sampleFile(profiles = listOf(pLarge))))
        assertTrue(outcomeLarge is ParseOutcome.Ready)
        assertNull(
            (outcomeLarge as ParseOutcome.Ready)
                .backup.profiles[0]
                .avatar.photoJpegBytes,
        )

        val validJpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(10) { 1 }
        val pValid =
            sampleProfile(
                avatar =
                    BackupAvatar(
                        colorIndex = 0,
                        photoJpegBase64 = Base64.getEncoder().encodeToString(validJpegBytes),
                    ),
            )
        val outcomeValid = BackupParser.parse(encode(sampleFile(profiles = listOf(pValid))))
        assertTrue(outcomeValid is ParseOutcome.Ready)
        assertNotNull(
            (outcomeValid as ParseOutcome.Ready)
                .backup.profiles[0]
                .avatar.photoJpegBytes,
        )
        assertEquals(
            13,
            outcomeValid.backup.profiles[0]
                .avatar.photoJpegBytes
                ?.size,
        )
    }

    @Test
    fun VA20_overLimit_isRejected() {
        val item1 =
            BackupLibraryItem(
                "m1",
                "movie",
                "N1",
                inWatchlist = true,
                addedAt = 1L,
                listStatus = ListStatus.PLAN_TO_WATCH.name,
            )
        val item2 = item1.copy(metaId = "m2")
        val item3 = item1.copy(metaId = "m3")
        val p = sampleProfile(library = listOf(item1, item2, item3))
        val outcome =
            BackupParser.parse(
                encode(sampleFile(profiles = listOf(p))),
                limits = BackupLimits(maxItemsPerList = 2),
            )
        assertEquals(ParseOutcome.Rejected(RejectReason.TooManyItems), outcome)

        val outcomeLarge =
            BackupParser.parse(
                encode(sampleFile(profiles = listOf(p))),
                limits = BackupLimits(maxBytes = 10L),
            )
        assertEquals(ParseOutcome.Rejected(RejectReason.TooLarge), outcomeLarge)
    }

    @Test
    fun VA21_duplicates_keepNewest() {
        val lib1 =
            BackupLibraryItem(
                "m1",
                "movie",
                "N1",
                inWatchlist = true,
                addedAt = 1L,
                listStatus = ListStatus.PLAN_TO_WATCH.name,
            )
        val lib2 = lib1.copy(addedAt = 2L)
        val prog1 =
            BackupProgressItem(
                "k1",
                "m1",
                "v1",
                "movie",
                positionMs = 100L,
                durationMs = 1000L,
                watched = false,
                updatedAt = 100L,
            )
        val prog2 = prog1.copy(updatedAt = 200L)
        val p = sampleProfile(library = listOf(lib1, lib2), progress = listOf(prog1, prog2))
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        val ready = outcome as ParseOutcome.Ready
        assertEquals(
            1,
            ready.backup.profiles[0]
                .library.size,
        )
        assertEquals(
            2L,
            ready.backup.profiles[0]
                .library[0]
                .addedAt,
        )
        assertEquals(
            1,
            ready.backup.profiles[0]
                .progress.size,
        )
        assertEquals(
            200L,
            ready.backup.profiles[0]
                .progress[0]
                .updatedAt,
        )
    }

    @Test
    fun VA22_skippedCount_isTotalled() {
        val badLib =
            BackupLibraryItem(
                "",
                "movie",
                "N1",
                inWatchlist = true,
                addedAt = 1L,
                listStatus = ListStatus.PLAN_TO_WATCH.name,
            )
        val badProg =
            BackupProgressItem(
                "k1",
                "m1",
                "v1",
                "movie",
                positionMs = -1L,
                durationMs = 1000L,
                watched = false,
                updatedAt = 100L,
            )
        val badAddon = BackupAddonItem("ftp://bad", "a1", "N1", "1.0", "{}", true, 0, AddonSource.LOCAL.name, 1000L)
        val badPref = BackupSettingsStore("settings", listOf(BackupPrefEntry("theme_mode", "int", JsonPrimitive(1))))

        val p =
            sampleProfile(
                library = listOf(badLib),
                progress = listOf(badProg),
            ).copy(
                addons = listOf(badAddon),
                settings = listOf(badPref),
            )
        val outcome = BackupParser.parse(encode(sampleFile(profiles = listOf(p))))
        assertTrue(outcome is ParseOutcome.Ready)
        assertEquals(4, (outcome as ParseOutcome.Ready).skipped)
    }
}
