package com.drift.stream.data.local.db

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DaoScopeGuardTest {
    private fun resolveDaoSource(fileName: String): String {
        val candidates =
            listOf(
                File("src/main/java/com/drift/stream/data/local/db/dao/$fileName"),
                File("app/src/main/java/com/drift/stream/data/local/db/dao/$fileName"),
                File(System.getProperty("user.dir"), "src/main/java/com/drift/stream/data/local/db/dao/$fileName"),
                File(System.getProperty("user.dir"), "app/src/main/java/com/drift/stream/data/local/db/dao/$fileName"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("Cannot find $fileName in: $candidates (user.dir=${System.getProperty("user.dir")})")
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
}
