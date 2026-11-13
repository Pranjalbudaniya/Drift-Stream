package com.drift.stream.data.local.db

import androidx.sqlite.SQLiteConnection
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class Migration7To8Test {
    @Test
    fun MG3_migration7To8_keepsExistingProfiles() {
        val connection = mockk<SQLiteConnection>(relaxed = true)
        val migration = NexusDatabase_AutoMigration_7_8_Impl()

        migration.migrate(connection)

        verify(atLeast = 1) {
            connection.prepare(match { it.contains("ALTER TABLE `profiles` ADD COLUMN `pinFailedAttempts`") })
        }
        verify(atLeast = 1) {
            connection.prepare(match { it.contains("ALTER TABLE `profiles` ADD COLUMN `pinLockedUntil`") })
        }
    }

    @Test
    fun MG4_migration7To8_newColumnsAreNull() {
        val connection = mockk<SQLiteConnection>(relaxed = true)
        val migration = NexusDatabase_AutoMigration_7_8_Impl()

        migration.migrate(connection)

        verify(atLeast = 1) {
            connection.prepare(
                match {
                    it.contains("`pinFailedAttempts` INTEGER") &&
                        !it.contains("`pinFailedAttempts` INTEGER NOT NULL")
                },
            )
        }
        verify(atLeast = 1) {
            connection.prepare(
                match {
                    it.contains("`pinLockedUntil` INTEGER") &&
                        !it.contains("`pinLockedUntil` INTEGER NOT NULL")
                },
            )
        }
    }
}
