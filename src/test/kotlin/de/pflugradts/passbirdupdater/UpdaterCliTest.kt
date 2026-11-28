package de.pflugradts.passbirdupdater

import de.pflugradts.passbirdupdater.OfflineUpdater.DOWNLOAD_URL
import de.pflugradts.passbirdupdater.OfflineUpdater.RELEASES_API
import de.pflugradts.passbirdupdater.OfflineUpdater.STABLE_API
import de.pflugradts.passbirdupdater.OfflineUpdater.UPDATER_API
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdaterCliTest {
    @TempDir
    lateinit var directory: Path

    private val install get() = Files.createDirectories(directory.resolve("install"))
    private val jar get() = Path.of(System.getProperty("passbird.test.jar"))

    // REQ-0001, REQ-0002, REQ-0006, REQ-0009
    @Test
    fun `default channel installs the verified public release and retains the latest three versions`() {
        fixture("stable.json", release("v6.4.0"))
        fixture("download.jar", "hello")
        fixture("updater.json", updaterRelease("0".repeat(64)))
        localJar("6.1.0")
        localJar("6.2.0")
        localJar("6.3.0")
        localJar("6.3.0-dev.20260525.1")
        currentJar("previous install")

        val result = runUpdater(install.toString())

        assertEquals(0, result.status, result.output)
        assertContains(result.output, UPDATER_URL)
        assertEquals(listOf(UPDATER_API, STABLE_API, DOWNLOAD_URL), result.requests)
        assertEquals("hello", Files.readString(install.resolve("passbird-6.4.0.jar")))
        assertEquals("hello", Files.readString(install.resolve("passbird.jar")))
        assertEquals(
            setOf("passbird.jar", "passbird-6.3.0.jar", "passbird-6.3.0-dev.20260525.1.jar", "passbird-6.4.0.jar"),
            installedFiles(),
        )
    }

    // REQ-0002, REQ-0009
    @Test
    fun `dev channel chooses the highest eligible prerelease even when releases are out of order`() {
        fixture(
            "releases.json",
            listOf(
                release("v6.4.0-dev.20260526.10"),
                release("v9.0.0", draft = true),
                release("v6.4.0-dev.20260526.2"),
                release("v8.0.0", assets = """[{"name":"notes.txt"}]"""),
                release("snapshot"),
                release("v6.4.0-dev.20260525.99"),
                release("v6.4.0"),
            ).joinToString(prefix = "[", postfix = "]"),
        )
        fixture("download.jar", "hello")
        val jarDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)))
        fixture("updater.json", updaterRelease(jarDigest))

        val result = runUpdater(install.toString(), "--channel", "dev")

        assertEquals(0, result.status, result.output)
        assertEquals(listOf(UPDATER_API, RELEASES_API, DOWNLOAD_URL), result.requests)
        assertEquals(setOf("passbird.jar", "passbird-6.4.0-dev.20260526.10.jar"), installedFiles())
        assertFalse(result.output.contains(UPDATER_URL), result.output)
    }

    // REQ-0002, REQ-0009
    @Test
    fun `dev channel can select a newer stable release when the updater notice is unavailable`() {
        fixture("releases.json", "[${release("v6.5.0")}, ${release("v6.4.0-dev.20260525.1")}]")
        fixture("download.jar", "hello")
        fixture("updater.json", """{"assets": [{"name": "notes.txt"}]}""")

        val result = runUpdater(install.toString(), "--channel", "dev")

        assertEquals(0, result.status, result.output)
        assertEquals(setOf("passbird.jar", "passbird-6.5.0.jar"), installedFiles())
        assertFalse(result.output.contains("Passbird Updater release asset"), result.output)
        assertFalse(result.output.contains("A new Passbird Updater version"), result.output)
    }

    // REQ-0004, REQ-0005, REQ-0006
    @Test
    fun `switching to stable reactivates the cached jar without downloading it again`() {
        fixture("stable.json", release("6.4.0"))
        localJar("6.4.0", "hello")
        localJar("6.4.0-dev.20260525.1", "dev")
        currentJar("dev")

        val result = runUpdater(install.toString(), "--channel=stable", "2")

        assertEquals(0, result.status, result.output)
        assertEquals(listOf(UPDATER_API, STABLE_API), result.requests)
        assertEquals("hello", Files.readString(install.resolve("passbird.jar")))
        assertEquals(setOf("passbird.jar", "passbird-6.4.0.jar", "passbird-6.4.0-dev.20260525.1.jar"), installedFiles())
    }

    // REQ-0001, REQ-0007
    @Test
    fun `checksum failure removes the download and leaves the installation and backups alone`() {
        fixture("stable.json", release("v6.4.0"))
        fixture("download.jar", "corrupt response")
        currentJar("previous install")
        localJar("6.3.0")
        localJar("6.2.0")

        val result = runUpdater(install.toString(), "1")

        assertEquals(1, result.status, result.output)
        assertContains(result.output, "doesn't have the expected checksum")
        assertEquals("previous install", Files.readString(install.resolve("passbird.jar")))
        assertEquals(setOf("passbird.jar", "passbird-6.3.0.jar", "passbird-6.2.0.jar"), installedFiles())
    }

    // REQ-0007
    @Test
    fun `a release without the required asset fails on either channel`() {
        val releaseWithoutJar = release("v6.4.0", assets = "[]")
        fixture("stable.json", releaseWithoutJar)
        fixture("releases.json", "[$releaseWithoutJar]")
        currentJar("previous install")

        for (channel in listOf("stable", "dev")) {
            val result = runUpdater(install.toString(), "--channel", channel)

            assertEquals(1, result.status, result.output)
            assertContains(result.output, "asset 'passbird.jar'")
            assertFalse(DOWNLOAD_URL in result.requests)
            assertEquals(setOf("passbird.jar"), installedFiles())
            assertEquals("previous install", Files.readString(install.resolve("passbird.jar")))
        }
    }

    // REQ-0007
    @Test
    fun `failed download reports an error and preserves the installed jar`() {
        fixture("stable.json", release("v6.4.0"))
        currentJar("previous install")

        val result = runUpdater(install.toString())

        assertEquals(1, result.status, result.output)
        assertContains(result.output, "Oops, something went wrong!")
        assertEquals(listOf(UPDATER_API, STABLE_API, DOWNLOAD_URL), result.requests)
        assertEquals(setOf("passbird.jar"), installedFiles())
        assertEquals("previous install", Files.readString(install.resolve("passbird.jar")))
    }

    // REQ-0003, REQ-0008
    @Test
    fun `invalid command lines fail with a diagnostic before requesting release metadata`() {
        val regularFile = fixture("file", "a regular file")
        val cases = listOf(
            emptyList<String>() to "Passbird directory not specified!",
            listOf(directory.resolve("missing").toString()) to "Specified directory does not exist",
            listOf(regularFile.toString()) to "Specified directory does not exist",
            listOf(install.toString(), "--channel") to "Missing value for --channel",
        )

        for ((args, diagnostic) in cases) {
            val result = runUpdater(*args.toTypedArray())

            assertEquals(1, result.status, result.output)
            assertContains(result.output, diagnostic)
            assertTrue(result.requests.isEmpty(), result.requests.toString())
        }
    }

    private fun runUpdater(vararg args: String): RunResult {
        val log = directory.resolve("requests.log")
        Files.deleteIfExists(log)
        val output = directory.resolve("output.log")
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val fixtureClasses = Path.of(OfflineUpdater::class.java.protectionDomain.codeSource.location.toURI())
        // Gradle's JaCoCo agent appends coverage from the child as well as the test worker.
        val agents = ManagementFactory.getRuntimeMXBean().inputArguments.filter { it.startsWith("-javaagent:") && "jacoco" in it }
        val process = ProcessBuilder(
            java.toString(), *agents.toTypedArray(), "-cp", "$jar${File.pathSeparator}$fixtureClasses",
            OfflineUpdater::class.java.name, directory.toString(), *args,
        ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            assertTrue(process.waitFor(30, SECONDS), "Updater process timed out")
            return RunResult(
                process.exitValue(),
                Files.readString(output),
                if (Files.exists(log)) Files.readAllLines(log) else emptyList(),
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun installedFiles() = Files.list(install).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() }
    private fun localJar(version: String, content: String = version) = Files.writeString(install.resolve("passbird-$version.jar"), content)
    private fun currentJar(content: String) = Files.writeString(install.resolve("passbird.jar"), content)
    private fun fixture(name: String, content: String) = Files.writeString(directory.resolve(name), content)

    private data class RunResult(val status: Int, val output: String, val requests: List<String>)

    private companion object {
        const val UPDATER_URL = "https://fixtures.invalid/passbird-updater.jar"
        const val HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

        fun asset(name: String, url: String, digest: String) =
            """{"name":"$name","browser_download_url":"$url","digest":"sha256:$digest"}"""

        fun release(
            tag: String,
            draft: Boolean = false,
            assets: String = "[${asset("passbird.jar", DOWNLOAD_URL, HELLO_SHA256)}]",
        ) = """{"tag_name":"$tag","draft":$draft,"assets":$assets}"""

        fun updaterRelease(digest: String) =
            """{"assets":[${asset("passbird-updater.jar", UPDATER_URL, digest)}]}"""
    }
}
