package de.pflugradts.passbirdupdater

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PassbirdUpdaterTest {
    @TempDir
    lateinit var directory: Path

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
        val sameStableVersion = assertNotNull(parsePassbirdVersion("6.3.0"))
        val devVersion = assertNotNull(parsePassbirdVersion("6.3.0-dev.20260525.1"))
        val newerStableVersion = assertNotNull(parsePassbirdVersion("6.4.0"))

        assertEquals(0, stableVersion.compareTo(sameStableVersion))
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
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("--channel=dev", "--channel", "stable")) }
        assertFailsWith<IllegalArgumentException> { parseProgramOptions(listOf("2", "3")) }
    }

    // REQ-0007
    @Test
    fun `malformed release asset metadata identifies the field that failed`() {
        val cases = mapOf(
            """{"tag_name":"v6.4.0"}""" to "assets",
            """{"tag_name":"v6.4.0","assets":[{"name":"passbird.jar","digest":"sha256:abc"}]}""" to "browser_download_url",
            """{"tag_name":"v6.4.0","assets":[{"name":"passbird.jar","browser_download_url":"https://example.invalid","digest":"abc"}]}""" to "digest",
        )

        for ((metadata, field) in cases) {
            val exception = assertFailsWith<IllegalStateException> { parseRelease(parseJsonObject(metadata)) }
            assertContains(exception.message.orEmpty(), "'$field'")
        }
    }

    // REQ-0001
    @Test
    fun `checksums cover the whole download and use the published algorithm`() {
        val jar = Files.writeString(directory.resolve("download.jar"), "Passbird\n".repeat(4096))

        assertEquals("eda7e06ad1083129a7b1ed874a06b384", checksum(jar, "md5"))
        assertEquals("d770decfcb52324ae4ae324671c0a32817738adeb845afdae2c2d0680eea126b", checksum(jar, "SHA256"))
        assertEquals(
            "0abe1470c9d12d2fe744085b85f4044f16a9920c3fc160ff88e0c412f545ec5202feea246ca40536c2af56556dbf4a211f43829ee1e2949f5cbc63131e0e3b46",
            checksum(jar, "sha512"),
        )
        val exception = assertFailsWith<IllegalStateException> { checksum(jar, "sha1") }
        assertContains(exception.message.orEmpty(), "Unsupported release digest algorithm: sha1")
    }

    // REQ-0001, REQ-0004
    @Test
    fun `activateRelease copies a jar with the expected checksum to passbird jar`() {
        val jar = directory.resolve("passbird-6.4.0.jar").also { Files.writeString(it, "release") }
        val release = release("6.4.0", checksum(jar, "sha256"))

        assertTrue(activateRelease(release, jar, directory.toString()))

        assertEquals("release", Files.readString(directory.resolve("passbird.jar")))
    }

    // REQ-0001
    @Test
    fun `activateRelease removes a jar with another checksum and keeps passbird jar`() {
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
