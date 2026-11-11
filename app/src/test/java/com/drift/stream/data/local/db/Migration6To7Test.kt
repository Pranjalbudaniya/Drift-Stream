package com.drift.stream.data.local.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class Migration6To7Test {
    @Test
    fun MG1_migration6To7_keepsExistingProfiles() {
        val connection = mockk<SQLiteConnection>(relaxed = true)
        val migration = NexusDatabase_AutoMigration_6_7_Impl()

        migration.migrate(connection)

        verify(atLeast = 1) {
            connection.prepare(match { it.contains("ALTER TABLE `profiles` ADD COLUMN `avatarCustomColor`") })
        }
        verify(atLeast = 1) {
            connection.prepare(match { it.contains("ALTER TABLE `profiles` ADD COLUMN `avatarPhotoStamp`") })
        }
    }

    @Test
    fun MG2_migration6To7_newColumnsAreNull() {
        val connection = mockk<SQLiteConnection>(relaxed = true)
        val migration = NexusDatabase_AutoMigration_6_7_Impl()

        migration.migrate(connection)

        verify(atLeast = 1) {
            connection.prepare(
                match {
                    it.contains("`avatarCustomColor` INTEGER") &&
                        !it.contains("`avatarCustomColor` INTEGER NOT NULL")
                },
            )
        }
        verify(atLeast = 1) {
            connection.prepare(
                match {
                    it.contains("`avatarPhotoStamp` INTEGER") &&
                        !it.contains("`avatarPhotoStamp` INTEGER NOT NULL")
                },
            )
        }
    }
}
