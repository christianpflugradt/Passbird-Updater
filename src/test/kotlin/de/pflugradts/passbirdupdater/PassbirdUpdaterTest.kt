package de.pflugradts.passbirdupdater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PassbirdUpdaterTest {
    @Test
    fun `parseProgramOptions keeps previous defaults`() {
        val options = parseProgramOptions(emptyList())

        assertEquals(3, options.versionsToKeep)
        assertEquals(ReleaseChannel.STABLE, options.channel)
    }

    @Test
    fun `parseProgramOptions accepts channel after versions to keep`() {
        val options = parseProgramOptions(listOf("4", "--channel", "dev"))

        assertEquals(4, options.versionsToKeep)
        assertEquals(ReleaseChannel.DEV, options.channel)
    }

    @Test
    fun `parseProgramOptions accepts inline channel syntax`() {
        val options = parseProgramOptions(listOf("--channel=dev", "5"))

        assertEquals(5, options.versionsToKeep)
        assertEquals(ReleaseChannel.DEV, options.channel)
    }

    @Test
    fun `parseProgramOptions rejects unknown channels`() {
        assertFailsWith<IllegalArgumentException> {
            parseProgramOptions(listOf("--channel", "beta"))
        }
    }

    @Test
    fun `parsePassbirdVersion parses stable and dev labels`() {
        val stableVersion = assertNotNull(parsePassbirdVersion("6.3.0"))
        val devVersion = assertNotNull(parsePassbirdVersion("6.3.0-dev.20260525.1"))

        assertEquals("6.3.0", stableVersion.toString())
        assertEquals("6.3.0-dev.20260525.1", devVersion.toString())
    }

    @Test
    fun `passbird version ordering keeps dev releases above stable releases with the same base version`() {
        val stableVersion = assertNotNull(parsePassbirdVersion("6.3.0"))
        val devVersion = assertNotNull(parsePassbirdVersion("6.3.0-dev.20260525.1"))
        val newerStableVersion = assertNotNull(parsePassbirdVersion("6.4.0"))

        assertTrue(stableVersion < devVersion)
        assertTrue(devVersion < newerStableVersion)
    }

    @Test
    fun `parseReleaseOrNull accepts prefixed dev tags`() {
        val release = Json.parseToJsonElement(
            """
            {
              "tag_name": "dev-6.3.0-dev.20260525.2",
              "draft": false,
              "assets": [
                {
                  "name": "passbird.jar",
                  "browser_download_url": "https://example.invalid/passbird.jar",
                  "digest": "sha256:abc123"
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val parsedRelease = parseReleaseOrNull(release)

        assertEquals("6.3.0-dev.20260525.2", parsedRelease?.version.toString())
    }

    @Test
    fun `findLocalJars sorts stable and dev files by release precedence`() {
        val directory = Files.createTempDirectory("passbird-updater-test")
        Files.createFile(directory.resolve("passbird-6.3.0.jar"))
        Files.createFile(directory.resolve("passbird-6.3.0-dev.20260525.1.jar"))
        Files.createFile(directory.resolve("passbird-6.4.0.jar"))
        Files.createFile(directory.resolve("passbird.jar"))
        Files.createFile(directory.resolve("passbird-updater.jar"))

        val jars = findLocalJars(directory.toString())

        assertEquals(
            listOf(
                "passbird-6.3.0.jar",
                "passbird-6.3.0-dev.20260525.1.jar",
                "passbird-6.4.0.jar",
            ),
            jars,
        )
    }
}
