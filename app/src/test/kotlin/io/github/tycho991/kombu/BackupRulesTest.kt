package io.github.tycho991.kombu

import io.github.tycho991.kombu.data.account.ACCOUNT_DATASTORE_NAME
import io.github.tycho991.kombu.data.recipes.db.RECIPE_DATABASE_NAME
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The backup rules keep the credentials and the recipe database on the device (spec recipe-sync, "Local recipe
 * data stays on the device"). Unit tests run in the module directory, so the rule files are read from there.
 */
class BackupRulesTest {
    private val databaseFiles = listOf("", "-journal", "-wal", "-shm").map { RECIPE_DATABASE_NAME + it }

    private fun excludes(file: String, section: String): Set<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val parent = if (section == document.documentElement.tagName) {
            document.documentElement
        } else {
            document.documentElement.getElementsByTagName(section).item(0) as Element
        }
        val nodes = parent.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .map { it.getAttribute("domain") to it.getAttribute("path") }
            .toSet()
    }

    private val expected: Set<Pair<String, String>> =
        setOf("file" to "datastore/$ACCOUNT_DATASTORE_NAME.preferences_pb") + databaseFiles.map { "database" to it }

    @Test
    fun fullBackupExcludesTheCredentialsAndTheRecipeDatabase() {
        assertEquals(expected, excludes("backup_rules.xml", "full-backup-content"))
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeTheCredentialsAndTheRecipeDatabase() {
        assertEquals(expected, excludes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(expected, excludes("data_extraction_rules.xml", "device-transfer"))
    }
}
