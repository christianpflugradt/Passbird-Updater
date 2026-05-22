package de.pflugradts.passbirdupdater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.system.exitProcess
import kotlin.text.Charsets.UTF_8
import kotlin.streams.toList

const val TAB = "\t"
const val PASSBIRD_REPOSITORY = "christianpflugradt/Passbird"
const val PASSBIRD_RELEASES_URL = "https://github.com/$PASSBIRD_REPOSITORY/releases"
const val PASSBIRD_RELEASES_API_URL = "https://api.github.com/repos/$PASSBIRD_REPOSITORY/releases/latest"
const val PASSBIRD_RELEASE_ASSET = "passbird.jar"
const val PASSBIRD_UPDATER_REPOSITORY_URL = "https://github.com/christianpflugradt/Passbird-Updater"
val PASSBIRD_JAR_PATTERN = """passbird-(\d+)\.(\d+)\.(\d+)\.jar""".toRegex()

const val LOGO_START = """
$TAB  -,"
$TAB ( '<    Passbird Updater
$TAB / ) )
"""

const val DISCLAIMER = """
${TAB}DISCLAIMER ON

${TAB}Passbird Updater is not an official part of Passbird.
${TAB}Downloading a file from the internet is a security risk.
${TAB}Make sure GitHub is resolved correctly from your computer and
${TAB}the repository '$PASSBIRD_REPOSITORY' is the official and
${TAB}trusted source of Passbird releases.

${TAB}DISCLAIMER OFF
"""

const val GENERIC_ERROR = """
${TAB}If the latest Passbird version couldn't be downloaded,
${TAB}GitHub might be temporarily offline or someone made a mistake.

${TAB}If the problem persists check the GitHub repository for
${TAB}a new version of the Passbird Updater or open an issue there:

${TAB}$PASSBIRD_UPDATER_REPOSITORY_URL
"""

const val LOGO_END = """
$TAB                  ,-
${TAB}chirp chirirp    >' )
$TAB                 ( ( \
"""

data class ReleaseDigest(val algorithm: String, val value: String)
data class LatestRelease(val version: String, val downloadUrl: String, val digest: ReleaseDigest)
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion) = compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
}

fun main(args: Array<String>) {
    checkPassbirdDirectory(args)
    val versionsToKeep = args.getOrNull(1)?.toIntOrNull() ?: 3
    val passbirdDirectory = args[0]
    println(LOGO_START)
    println(DISCLAIMER)
    printlnwt("received passbird directory: $passbirdDirectory")
    printwt("determining latest version... ")
    try {
        val latestRelease = retrieveLatestRelease()
        println(latestRelease.version)
        val passbirdJarFile = Paths.get(passbirdDirectory, "passbird-${latestRelease.version}.jar")
        if (Files.exists(passbirdJarFile)) {
            printlnwt("version ${latestRelease.version} already downloaded")
        } else {
            printwt("downloading version ${latestRelease.version}... ")
            downloadLatestJar(latestRelease, passbirdJarFile)
            println("done")
            checksums(latestRelease, passbirdJarFile).let {
                if (it.first == it.second) {
                    Files.copy(passbirdJarFile, Paths.get(passbirdDirectory, "passbird.jar"), REPLACE_EXISTING)
                    printlnwt("updated passbird.jar to latest version ${latestRelease.version}")
                    deleteOldJars(passbirdDirectory, findLocalJars(passbirdDirectory), versionsToKeep)
                } else {
                    printlnwt("version ${latestRelease.version} doesn't have the expected checksum")
                    printlnwt("expected checksum: ${it.first}")
                    printlnwt("actual   checksum: ${it.second}")
                    printwt("downloaded version ${latestRelease.version} will be removed... ")
                    Files.delete(passbirdJarFile)
                    println("done")
                }
            }
        }
    } catch (ex: Exception) {
        printlnwt("Oops, something went wrong!\n")
        printlnwt("Exception class: ${ex.javaClass.name}")
        printlnwt("Exception message: ${ex.message}")
        println(GENERIC_ERROR)
    }
    printlnwt(LOGO_END)
}

fun checkPassbirdDirectory(args: Array<String>) = Paths.get(args.getOrElse(0) {
    printlnwt("Passbird directory not specified!")
    printlnwt("Please pass the path to Passbird jar directory to Passbird Updater.\n")
    printlnwt("Example:\nPassbird jar is located in: /opt/passbird/passbird.jar")
    printlnwt("Run Passbird Updater as follows: java -jar passbird-updater.jar /opt/passbird/")
    exitProcess(1)
}).let {
    if (!(Files.exists(it) && Files.isDirectory(it))) {
        printlnwt("Specified directory does not exist or cannot be accessed: $it")
        exitProcess(1)
    }
}
fun urlAsStream(url: String) = BufferedInputStream(URL(url).openConnection().apply {
    setRequestProperty("Accept", "application/vnd.github+json")
    setRequestProperty("User-Agent", "Passbird-Updater")
    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
}.getInputStream())
fun urlAsString(url: String) = urlAsStream(url).use { it.readAllBytes().toString(UTF_8) }
fun retrieveLatestRelease(): LatestRelease = Json.parseToJsonElement(urlAsString(PASSBIRD_RELEASES_API_URL)).jsonObject.let { release ->
    val asset = release.requiredArray("assets").firstOrNull { it.requiredString("name") == PASSBIRD_RELEASE_ASSET }
        ?: error("Passbird release asset '$PASSBIRD_RELEASE_ASSET' was not found at $PASSBIRD_RELEASES_URL")
    LatestRelease(
        version = release.requiredString("tag_name"),
        downloadUrl = asset.requiredString("browser_download_url"),
        digest = asset.requiredDigest("digest"),
    )
}
fun downloadLatestJar(latestRelease: LatestRelease, file: Path) = urlAsStream(latestRelease.downloadUrl).use { Files.copy(it, file) }
fun checksums(latestRelease: LatestRelease, file: Path) = Pair(
    latestRelease.digest.value,
    checksum(file, latestRelease.digest.algorithm),
)
fun checksum(file: Path, algorithm: String): String {
    val digest = MessageDigest.getInstance(messageDigestName(algorithm))
    Files.newInputStream(file).use { input ->
        DigestInputStream(input, digest).use { digestingStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (digestingStream.read(buffer) != -1) {
                // read the whole stream to update the digest
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
fun messageDigestName(algorithm: String) = when (algorithm.lowercase(Locale.ROOT)) {
    "md5" -> "MD5"
    "sha256" -> "SHA-256"
    "sha512" -> "SHA-512"
    else -> error("Unsupported release digest algorithm: $algorithm")
}
fun findLocalJars(passbirdDirectory: String): List<String> = Files.find(Paths.get(passbirdDirectory), 1, { path, _ ->
    PASSBIRD_JAR_PATTERN.matches(path.fileName.toString())
}).use { paths ->
    paths.map { it.fileName.toString() }.toList().sortedBy(::toSemanticVersion)
}
fun deleteOldJars(passbirdDirectory: String, jars: List<String>, versionsToKeep: Int) = jars.let {
    if (it.size > versionsToKeep) {
        it.subList(0, it.size - versionsToKeep).forEach { oldJar ->
            printwt("deleting old version '$oldJar'... ")
            println("done")
            Files.delete(Paths.get(passbirdDirectory, oldJar))
        }
    }
}
fun JsonObject.requiredArray(key: String) = this[key]?.jsonArray?.map { it.jsonObject } ?: error("Missing release metadata field '$key'")
fun JsonObject.requiredString(key: String) = this[key]?.jsonPrimitive?.content ?: error("Missing release metadata field '$key'")
fun JsonObject.requiredDigest(key: String): ReleaseDigest = requiredString(key).split(':', limit = 2).takeIf { it.size == 2 }?.let {
    ReleaseDigest(algorithm = it[0], value = it[1])
} ?: error("Invalid release metadata digest for '$key'")
fun toSemanticVersion(fileName: String) = PASSBIRD_JAR_PATTERN.matchEntire(fileName)?.destructured?.let { (major, minor, patch) ->
    SemanticVersion(major.toInt(), minor.toInt(), patch.toInt())
} ?: error("Passbird jar filename does not contain a semantic version: $fileName")
fun printwt(text: String) = print("$TAB$text")
fun printlnwt(text: String) = printwt("$text\n")
