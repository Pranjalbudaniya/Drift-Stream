package com.drift.stream.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DaoScopeGuardTest {
    private fun resolveDaoDirectory(): File {
        val candidates =
            listOf(
                File("src/main/java/com/drift/stream/data/local/db/dao"),
                File("app/src/main/java/com/drift/stream/data/local/db/dao"),
                File(System.getProperty("user.dir"), "src/main/java/com/drift/stream/data/local/db/dao"),
                File(System.getProperty("user.dir"), "app/src/main/java/com/drift/stream/data/local/db/dao"),
            )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot find dao directory in: $candidates (user.dir=${System.getProperty("user.dir")})")
    }

    private fun resolveDaoSource(fileName: String): String {
        val file = File(resolveDaoDirectory(), fileName)
        if (!file.exists()) {
            error("Cannot find $fileName in ${resolveDaoDirectory().path}")
        }
        return file.readText()
    }

    @Test
    fun DS1_everyQueryInTheScopedDaos_mentionsProfileId() {
        val daoFiles = listOf("LibraryDao.kt", "ProgressDao.kt", "SearchHistoryDao.kt")
        val queryRegex = Regex("""@Query\((.*?)\)\s*(?:fun|suspend fun)""", setOf(RegexOption.DOT_MATCHES_ALL))

        var totalQueries = 0
        var joinQueries = 0

        for (fileName in daoFiles) {
            val source = resolveDaoSource(fileName)
            val matches = queryRegex.findAll(source).toList()
            assertTrue("Expected queries in $fileName", matches.isNotEmpty())

            for (match in matches) {
                totalQueries++
                val queryBody = match.groupValues[1]
                assertTrue(
                    "Query in $fileName must contain profileId: $queryBody",
                    queryBody.contains("profileId"),
                )

                if (queryBody.contains("library_items") && queryBody.contains("watch_progress")) {
                    joinQueries++
                    assertTrue(
                        "Join in $fileName must contain 'l.profileId = p.profileId': $queryBody",
                        queryBody.contains("l.profileId = p.profileId"),
                    )
                }
            }
        }

        assertTrue("Expected to inspect multiple queries", totalQueries >= 15)
        assertTrue("Expected 2 join queries in ProgressDao", joinQueries == 2)
    }

    @Test
    fun DS2_addonDaos_everyQueryMentionsProfileId() {
        val daoFiles = listOf("AddonDao.kt", "ProfileAddonPrefsDao.kt")
        val queryRegex = Regex("""@Query\((.*?)\)\s*(?:fun|suspend fun)""", setOf(RegexOption.DOT_MATCHES_ALL))

        var totalQueries = 0

        for (fileName in daoFiles) {
            val source = resolveDaoSource(fileName)
            val matches = queryRegex.findAll(source).toList()
            assertTrue("Expected queries in $fileName", matches.isNotEmpty())

            for (match in matches) {
                totalQueries++
                val queryBody = match.groupValues[1]
                assertTrue(
                    "Query in $fileName must contain profileId: $queryBody",
                    queryBody.contains("profileId", ignoreCase = true),
                )
            }
        }

        assertTrue("Expected multiple queries in addon DAOs", totalQueries >= 8)
    }

    @Test
    fun DS3_downloadDao_everyQueryIsScopedOrOnTheEngineAllowList() {
        val source = resolveDaoSource("DownloadDao.kt")
        // Download IDs are generated as UUID.randomUUID().toString() in DefaultDownloadManager.enqueue,
        // so queries targeting a single download by ID are globally unique across profiles.
        // Engine-wide queries (getAll, observeEverything) are explicitly permitted for the download engine,
        // service lifecycle, and aggregate notifications.
        val engineAllowList =
            setOf(
                "get",
                "getAll",
                "observeEverything",
                "updateProgress",
                "updateState",
                "updateCompleted",
                "setAllowMetered",
                "delete",
            )
        val queryFunctionRegex =
            Regex("""@Query\((.*?)\)\s*(?:suspend\s+)?fun\s+(\w+)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val matches = queryFunctionRegex.findAll(source).toList()
        assertTrue("Expected queries in DownloadDao.kt", matches.isNotEmpty())

        var scopedCount = 0
        var allowListCount = 0

        for (match in matches) {
            val queryBody = match.groupValues[1]
            val functionName = match.groupValues[2]

            if (queryBody.contains("profileId")) {
                scopedCount++
            } else {
                assertTrue(
                    "Function '$functionName' without profileId must be in engineAllowList: $queryBody",
                    functionName in engineAllowList,
                )
                allowListCount++
            }
        }

        assertTrue("Expected scoped queries", scopedCount >= 4)
        assertEquals(engineAllowList.size, allowListCount)
    }

    @Test
    fun DS4_everyDaoFile_isClassified() {
        val profileScoped =
            setOf(
                "LibraryDao.kt",
                "ProgressDao.kt",
                "SearchHistoryDao.kt",
                "AddonDao.kt",
                "ProfileAddonPrefsDao.kt",
                "DownloadDao.kt",
            )
        val global = setOf("MetaCacheDao.kt")
        val profileTable = setOf("ProfileDao.kt", "ProfilePinQueries.kt")
        val notADao = setOf("SharedAddonRow.kt")

        val allCategories = listOf(profileScoped, global, profileTable, notADao)
        val allClassified = mutableSetOf<String>()
        for (set in allCategories) {
            for (file in set) {
                assertTrue("File '$file' must not appear in multiple sets", allClassified.add(file))
            }
        }

        val daoDir = resolveDaoDirectory()
        val actualFiles =
            daoDir
                .listFiles { _, name -> name.endsWith(".kt") }
                ?.map { it.name }
                ?.toSet()
                .orEmpty()

        assertTrue("Expected DAO files in directory", actualFiles.isNotEmpty())

        val unclassified = actualFiles - allClassified
        assertTrue("Found unclassified DAO files: $unclassified", unclassified.isEmpty())

        val nonExistent = allClassified - actualFiles
        assertTrue("Classified files do not exist in DAO directory: $nonExistent", nonExistent.isEmpty())
    }
}
