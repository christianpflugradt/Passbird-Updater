package de.pflugradts.passbirdupdater

import de.pflugradts.passbirdupdater.main as updaterMain
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

/** Subprocess entry point that runs the updater with only local response fixtures. */
object OfflineUpdater {
    const val STABLE_API = "https://api.github.com/repos/christianpflugradt/Passbird/releases/latest"
    const val RELEASES_API = "https://api.github.com/repos/christianpflugradt/Passbird/releases?per_page=100"
    const val UPDATER_API = "https://api.github.com/repos/christianpflugradt/Passbird-Updater/releases/latest"
    const val DOWNLOAD_URL = "https://fixtures.invalid/passbird.jar"

    @JvmStatic
    fun main(args: Array<String>) {
        val fixtures = Path.of(args.first())
        val responses = mapOf(
            UPDATER_API to "updater.json",
            STABLE_API to "stable.json",
            RELEASES_API to "releases.json",
            DOWNLOAD_URL to "download.jar",
        )
        URL.setURLStreamHandlerFactory { protocol ->
            if (protocol == "file" || protocol == "jar") null else object : URLStreamHandler() {
                override fun openConnection(url: URL) = object : URLConnection(url) {
                    override fun connect() = Unit

                    override fun getInputStream(): InputStream {
                        Files.writeString(fixtures.resolve("requests.log"), "$url\n", CREATE, APPEND)
                        val fixture = responses[url.toExternalForm()] ?: error("Unexpected request: $url")
                        return Files.newInputStream(fixtures.resolve(fixture))
                    }
                }
            }
        }
        updaterMain(args.drop(1).toTypedArray())
    }
}
