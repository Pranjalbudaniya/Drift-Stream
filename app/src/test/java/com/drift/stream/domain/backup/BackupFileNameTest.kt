package com.drift.stream.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupFileNameTest {
    private val testDate = LocalDate.of(2026, 11, 14)

    @Test
    fun BF1_allProfiles_name() {
        val result = backupFileName(scope = ExportScope.All, date = testDate)
        assertEquals("drift-stream-backup-2026-11-14.json", result)
    }

    @Test
    fun BF2_profileName_isSlugged() {
        val result1 = backupFileName(scope = ExportScope.Profile(1L), profileName = "Asha K!", date = testDate)
        assertEquals("drift-stream-asha-k-2026-11-14.json", result1)

        val result2 = backupFileName(scope = ExportScope.Profile(2L), profileName = "  Dev__2  ", date = testDate)
        assertEquals("drift-stream-dev-2-2026-11-14.json", result2)
    }

    @Test
    fun BF3_unusableName_fallsBackToProfile() {
        val result1 = backupFileName(scope = ExportScope.Profile(1L), profileName = "!!!", date = testDate)
        assertEquals("drift-stream-profile-2026-11-14.json", result1)

        val result2 = backupFileName(scope = ExportScope.Profile(2L), profileName = "", date = testDate)
        assertEquals("drift-stream-profile-2026-11-14.json", result2)

        val result3 = backupFileName(scope = ExportScope.Profile(3L), profileName = "😀", date = testDate)
        assertEquals("drift-stream-profile-2026-11-14.json", result3)
    }

    @Test
    fun BF4_longName_isCutTo20Characters() {
        val longName = "a".repeat(40)
        val result = backupFileName(scope = ExportScope.Profile(1L), profileName = longName, date = testDate)
        val expectedSlug = "a".repeat(20)
        assertEquals("drift-stream-$expectedSlug-2026-11-14.json", result)
    }
}
