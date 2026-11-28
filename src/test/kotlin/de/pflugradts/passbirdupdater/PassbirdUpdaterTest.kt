package de.pflugradts.passbirdupdater

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PassbirdUpdaterTest {
    // REQ-0003, REQ-0006
    @Test
    fun `parseProgramOptions keeps previous defaults`() {
        val options = parseProgramOptions(emptyList())

        assertEquals(3, options.versionsToKeep)
        assertEquals(ReleaseChannel.STABLE, options.channel)
    }

    // REQ-0003
    @Test
    fun `parseProgramOptions accepts channel after versions to keep`() {
        val options = parseProgramOptions(listOf("4", "--channel", "dev"))

        assertEquals(4, options.versionsToKeep)
        assertEquals(ReleaseChannel.DEV, options.channel)
    }

    // REQ-0003
    @Test
    fun `parseProgramOptions accepts inline channel syntax`() {
        val options = parseProgramOptions(listOf("--channel=dev", "5"))

        assertEquals(5, options.versionsToKeep)
        assertEquals(ReleaseChannel.DEV, options.channel)
    }

    // REQ-0003
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

    // REQ-0002
    @Test
    fun `passbird version ordering keeps dev releases above stable releases with the same base version`() {
        val stableVersion = assertNotNull(parsePassbirdVersion("6.3.0"))
        val devVersion = assertNotNull(parsePassbirdVersion("6.3.0-dev.20260525.1"))
        val newerStableVersion = assertNotNull(parsePassbirdVersion("6.4.0"))

        assertTrue(stableVersion < devVersion)
        assertTrue(devVersion < newerStableVersion)
    }

    // REQ-0002
    @Test
    fun `parseReleaseOrNull accepts prefixed dev tags`() {
        val release = parseJsonObject(
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
        )

        val parsedRelease = parseReleaseOrNull(release)

        assertEquals("6.3.0-dev.20260525.2", parsedRelease?.version.toString())
    }

    // REQ-0002
    @Test
    fun `parseReleaseOrNull accepts stable tags with and without a v prefix`() {
        fun release(tag: String) = parseJsonObject(
            """
            {
              "tag_name": "$tag",
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
        )

        assertEquals("6.3.0", parseReleaseOrNull(release("v6.3.0"))?.version.toString())
        assertEquals("6.3.0", parseReleaseOrNull(release("6.3.0"))?.version.toString())
    }

    // REQ-0006
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

    // REQ-0006
    @Test
    fun `parseProgramOptions rejects fewer than one version to keep`() {
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("0")) }
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("-1")) }
        assertEquals(1, parseProgramOptions(listOf("1")).versionsToKeep)
    }

    // REQ-0003
    @Test
    fun `parseProgramOptions rejects a bare channel and repeated options`() {
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("dev")) }
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("--channel", "dev", "--channel=stable")) }
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("2", "3")) }
    }

    // REQ-0001, REQ-0004
    @Test
    fun `activateRelease copies a jar with the expected checksum to passbird jar`() {
        val directory = Files.createTempDirectory("passbird-updater-test")
        val jar = directory.resolve("passbird-6.4.0.jar").also { Files.writeString(it, "release") }
        val release = release("6.4.0", checksum(jar, "sha256"))

        assertTrue(activateRelease(release, jar, directory.toString()))

        assertEquals("release", Files.readString(directory.resolve("passbird.jar")))
    }

    // REQ-0001
    @Test
    fun `activateRelease removes a jar with another checksum and keeps passbird jar`() {
        val directory = Files.createTempDirectory("passbird-updater-test")
        val current = directory.resolve("passbird.jar").also { Files.writeString(it, "current") }
        val jar = directory.resolve("passbird-6.4.0.jar").also { Files.writeString(it, "tampered") }

        assertFalse(activateRelease(release("6.4.0", "0".repeat(64)), jar, directory.toString()))

        assertFalse(Files.exists(jar))
        assertEquals("current", Files.readString(current))
    }

    private fun release(version: String, digest: String) = PassbirdRelease(
        version = assertNotNull(parsePassbirdVersion(version)),
        downloadUrl = "https://example.invalid/passbird.jar",
        digest = ReleaseDigest("sha256", digest),
    )
}
