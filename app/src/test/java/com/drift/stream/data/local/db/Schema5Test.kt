package com.drift.stream.data.local.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Schema5Test {
    private fun loadSchema5(): Map<String, kotlinx.serialization.json.JsonObject> {
        val candidates =
            listOf(
                File("schemas/com.drift.stream.data.local.db.NexusDatabase/5.json"),
                File("app/schemas/com.drift.stream.data.local.db.NexusDatabase/5.json"),
                File(System.getProperty("user.dir"), "schemas/com.drift.stream.data.local.db.NexusDatabase/5.json"),
                File(System.getProperty("user.dir"), "app/schemas/com.drift.stream.data.local.db.NexusDatabase/5.json"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("Cannot find 5.json in: $candidates (user.dir=${System.getProperty("user.dir")})")

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val entities = root["database"]!!.jsonObject["entities"]!!.jsonArray
        return entities.associate {
            val obj = it.jsonObject
            obj["tableName"]!!.jsonPrimitive.content to obj
        }
    }

    @Test
    fun SCH1_schema5_hasProfilesTableWithExpectedColumns() {
        val tables = loadSchema5()
        val profilesTable = tables["profiles"]
        assertNotNull("profiles table should exist in 5.json", profilesTable)

        val fields =
            profilesTable!!["fields"]!!.jsonArray.map {
                it.jsonObject["columnName"]!!.jsonPrimitive.content
            }
        val expected =
            listOf(
                "id",
                "name",
                "avatarColor",
                "avatarEmoji",
                "isOwner",
                "needsSetup",
                "pinHash",
                "pinSalt",
                "pinIterations",
                "createdAt",
                "lastUsedAt",
                "sortOrder",
            )
        assertEquals(expected, fields)
    }

    @Test
    fun SCH2_schema5_fiveTablesHaveCompositePrimaryKeyWithProfileId() {
        val tables = loadSchema5()
        val expectedKeys =
            mapOf(
                "addons" to listOf("profileId", "transportUrl"),
                "watch_progress" to listOf("profileId", "key"),
                "library_items" to listOf("profileId", "metaId"),
                "search_history" to listOf("profileId", "query"),
                "downloads" to listOf("profileId", "id"),
            )

        for ((table, expectedPk) in expectedKeys) {
            val entity = tables[table]
            assertNotNull("Table $table should exist", entity)
            val pkColumns =
                entity!!["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map {
                    it.jsonPrimitive.content
                }
            assertEquals("Table $table primary key must match", expectedPk, pkColumns)
        }
    }

    @Test
    fun SCH3_schema5_profileIdDefaultsToOne() {
        val tables = loadSchema5()
        val targetTables = listOf("addons", "watch_progress", "library_items", "search_history", "downloads")

        for (table in targetTables) {
            val entity = tables[table]
            assertNotNull("Table $table should exist", entity)
            val profileIdField =
                entity!!["fields"]!!
                    .jsonArray
                    .map { it.jsonObject }
                    .firstOrNull { it["columnName"]!!.jsonPrimitive.content == "profileId" }
            assertNotNull("Table $table must have profileId column", profileIdField)
            val notNull = profileIdField!!["notNull"]!!.jsonPrimitive.boolean
            assertTrue("Table $table profileId must be notNull", notNull)
            val defaultVal = profileIdField["defaultValue"]!!.jsonPrimitive.content
            assertEquals("Table $table profileId defaultValue must be 1", "1", defaultVal)
        }
    }

    @Test
    fun SCH4_schema6_hasSharedColumnAndPrefsTable() {
        val candidates =
            listOf(
                File("schemas/com.drift.stream.data.local.db.NexusDatabase/6.json"),
                File("app/schemas/com.drift.stream.data.local.db.NexusDatabase/6.json"),
                File(System.getProperty("user.dir"), "schemas/com.drift.stream.data.local.db.NexusDatabase/6.json"),
                File(System.getProperty("user.dir"), "app/schemas/com.drift.stream.data.local.db.NexusDatabase/6.json"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("Cannot find 6.json in: $candidates (user.dir=${System.getProperty("user.dir")})")

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val entities = root["database"]!!.jsonObject["entities"]!!.jsonArray
        val tables =
            entities.associate {
                val obj = it.jsonObject
                obj["tableName"]!!.jsonPrimitive.content to obj
            }

        val addons = tables["addons"]
        assertNotNull("addons table must exist in 6.json", addons)
        val sharedCol =
            addons!!["fields"]!!.jsonArray.map { it.jsonObject }.firstOrNull {
                it["columnName"]!!.jsonPrimitive.content == "shared"
            }
        assertNotNull("addons.shared column must exist", sharedCol)
        assertTrue("addons.shared must be notNull", sharedCol!!["notNull"]!!.jsonPrimitive.boolean)
        assertEquals("0", sharedCol["defaultValue"]!!.jsonPrimitive.content)

        val prefs = tables["profile_addon_prefs"]
        assertNotNull("profile_addon_prefs table must exist in 6.json", prefs)
        val pk = prefs!!["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("profileId", "ownerProfileId", "transportUrl"), pk)
    }
}
