package de.pflugradts.passbirdupdater

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
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
const val PASSBIRD_RELEASES_LIST_API_URL = "https://api.github.com/repos/$PASSBIRD_REPOSITORY/releases?per_page=100"
const val PASSBIRD_RELEASE_ASSET = "passbird.jar"
const val PASSBIRD_UPDATER_REPOSITORY = "christianpflugradt/Passbird-Updater"
const val PASSBIRD_UPDATER_REPOSITORY_URL = "https://github.com/$PASSBIRD_UPDATER_REPOSITORY"
const val PASSBIRD_UPDATER_RELEASES_API_URL = "https://api.github.com/repos/$PASSBIRD_UPDATER_REPOSITORY/releases/latest"
const val PASSBIRD_UPDATER_RELEASE_ASSET = "passbird-updater.jar"
val PASSBIRD_JAR_PATTERN = """passbird-(.+)\.jar""".toRegex()
val PASSBIRD_VERSION_PATTERN = """(\d+)\.(\d+)\.(\d+)(?:-dev\.(\d{8})\.(\d+))?""".toRegex()

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

object PassbirdUpdaterRuntime

enum class ReleaseChannel {
    STABLE,
    DEV,
    ;

    companion object {
        fun fromArgument(value: String) = when (value.lowercase(Locale.ROOT)) {
            "stable" -> STABLE
            "dev" -> DEV
            else -> throw IllegalArgumentException("Unsupported release channel '$value'. Use stable or dev.")
        }
    }
}

data class ProgramOptions(val versionsToKeep: Int = 3, val channel: ReleaseChannel = ReleaseChannel.STABLE)
data class ReleaseDigest(val algorithm: String, val value: String)
data class PassbirdRelease(val version: PassbirdVersion, val downloadUrl: String, val digest: ReleaseDigest)
data class UpdaterRelease(val downloadUrl: String, val digest: ReleaseDigest)
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion) = compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString() = "$major.$minor.$patch"
}

data class DevVersion(val date: String, val sequence: Int) : Comparable<DevVersion> {
    override fun compareTo(other: DevVersion) = compareValuesBy(this, other, DevVersion::date, DevVersion::sequence)
}

data class PassbirdVersion(val baseVersion: SemanticVersion, val devVersion: DevVersion? = null) : Comparable<PassbirdVersion> {
    override fun compareTo(other: PassbirdVersion) = compareValuesBy(this, other, PassbirdVersion::baseVersion).takeIf { it != 0 } ?: when {
        devVersion == null && other.devVersion == null -> 0
        devVersion == null -> -1
        other.devVersion == null -> 1
        else -> devVersion.compareTo(other.devVersion)
    }

    override fun toString() = devVersion?.let { "$baseVersion-dev.${it.date}.${it.sequence}" } ?: baseVersion.toString()
}

fun main(args: Array<String>) {
    val passbirdDirectory = requirePassbirdDirectory(args)
    val options = runCatching {
        parseProgramOptions(args.drop(1))
    }.getOrElse {
        printlnwt(it.message ?: "Invalid arguments.")
        exitProcess(1)
    }
    checkPassbirdDirectory(passbirdDirectory)
    println(LOGO_START)
    println(DISCLAIMER)
    announceUpdaterUpdateIfAvailable()
    printlnwt("received passbird directory: $passbirdDirectory")
    printwt("determining latest version... ")
    try {
        val latestRelease = retrieveLatestRelease(options.channel)
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
                    deleteOldJars(passbirdDirectory, findLocalJars(passbirdDirectory), options.versionsToKeep)
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

fun announceUpdaterUpdateIfAvailable() = runCatching {
    retrieveLatestUpdaterRelease().takeIf { latestRelease ->
        currentJarFile()?.let { checksum(it, latestRelease.digest.algorithm) != latestRelease.digest.value } ?: false
    }?.let { latestRelease ->
        printlnwt("A new Passbird Updater version is available: ${latestRelease.downloadUrl}")
    }
}

fun requirePassbirdDirectory(args: Array<String>) = args.firstOrNull() ?: run {
    printlnwt("Passbird directory not specified!")
    printlnwt("Please pass the path to Passbird jar directory to Passbird Updater.\n")
    printlnwt("Example:\nPassbird jar is located in: /opt/passbird/passbird.jar")
    printlnwt("Run Passbird Updater as follows: java -jar passbird-updater.jar /opt/passbird/")
    exitProcess(1)
}

fun parseProgramOptions(args: List<String>): ProgramOptions {
    var channel = ReleaseChannel.STABLE
    var versionsToKeep = 3
    var index = 0
    var channelSpecified = false
    var versionsSpecified = false
    while (index < args.size) {
        val argument = args[index]
        when {
            argument == "--channel" -> {
                val channelValue = args.getOrNull(index + 1) ?: throw IllegalArgumentException("Missing value for --channel. Use stable or dev.")
                if (channelSpecified) {
                    throw IllegalArgumentException("The release channel may only be specified once.")
                }
                channel = ReleaseChannel.fromArgument(channelValue)
                channelSpecified = true
                index += 2
            }

            argument.startsWith("--channel=") -> {
                if (channelSpecified) {
                    throw IllegalArgumentException("The release channel may only be specified once.")
                }
                channel = ReleaseChannel.fromArgument(argument.substringAfter("="))
                channelSpecified = true
                index += 1
            }

            argument.toIntOrNull() != null -> {
                if (versionsSpecified) {
                    throw IllegalArgumentException("The number of versions to keep may only be specified once.")
                }
                versionsToKeep = argument.toInt()
                versionsSpecified = true
                index += 1
            }

            else -> throw IllegalArgumentException("Unsupported argument '$argument'.")
        }
    }
    return ProgramOptions(versionsToKeep = versionsToKeep, channel = channel)
}

fun checkPassbirdDirectory(passbirdDirectory: String) = Paths.get(passbirdDirectory).let {
    if (!(Files.exists(it) && Files.isDirectory(it))) {
        printlnwt("Specified directory does not exist or cannot be accessed: $it")
        exitProcess(1)
    }
}
fun urlAsStream(url: String) = BufferedInputStream(java.net.URI.create(url).toURL().openConnection().apply {
    setRequestProperty("Accept", "application/vnd.github+json")
    setRequestProperty("User-Agent", "Passbird-Updater")
    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
}.getInputStream())
fun urlAsString(url: String) = urlAsStream(url).use { it.readAllBytes().toString(UTF_8) }
fun currentJarFile() = runCatching {
    Paths.get(PassbirdUpdaterRuntime::class.java.protectionDomain.codeSource.location.toURI())
}.getOrNull()?.takeIf(Files::isRegularFile)
fun retrieveLatestRelease(channel: ReleaseChannel) = when (channel) {
    ReleaseChannel.STABLE -> parseRelease(Json.parseToJsonElement(urlAsString(PASSBIRD_RELEASES_API_URL)).jsonObject)
    ReleaseChannel.DEV -> Json.parseToJsonElement(urlAsString(PASSBIRD_RELEASES_LIST_API_URL)).jsonArray
        .map { it.jsonObject }
        .mapNotNull(::parseReleaseOrNull)
        .maxByOrNull(PassbirdRelease::version)
        ?: error("No Passbird release with asset '$PASSBIRD_RELEASE_ASSET' was found at $PASSBIRD_RELEASES_URL")
}

fun retrieveLatestUpdaterRelease(): UpdaterRelease = Json.parseToJsonElement(urlAsString(PASSBIRD_UPDATER_RELEASES_API_URL)).jsonObject.let { release ->
    val asset = release.requiredArray("assets").firstOrNull { it.requiredString("name") == PASSBIRD_UPDATER_RELEASE_ASSET }
        ?: error("Passbird Updater release asset '$PASSBIRD_UPDATER_RELEASE_ASSET' was not found at $PASSBIRD_UPDATER_REPOSITORY_URL")
    UpdaterRelease(
        downloadUrl = asset.requiredString("browser_download_url"),
        digest = asset.requiredDigest("digest"),
    )
}
fun parseRelease(release: JsonObject): PassbirdRelease = parseReleaseOrNull(release)
    ?: error("Passbird release asset '$PASSBIRD_RELEASE_ASSET' was not found at $PASSBIRD_RELEASES_URL")
fun parseReleaseOrNull(release: JsonObject): PassbirdRelease? {
    if (release["draft"]?.jsonPrimitive?.booleanOrNull == true) {
        return null
    }
    val asset = release.requiredArray("assets").firstOrNull { it.requiredString("name") == PASSBIRD_RELEASE_ASSET } ?: return null
    val version = parsePassbirdVersion(normalizeReleaseTag(release.requiredString("tag_name"))) ?: return null
    return PassbirdRelease(
        version = version,
        downloadUrl = asset.requiredString("browser_download_url"),
        digest = asset.requiredDigest("digest"),
    )
}

fun normalizeReleaseTag(tagName: String) = tagName.removePrefix("dev-")
fun parsePassbirdVersion(version: String) = PASSBIRD_VERSION_PATTERN.matchEntire(version)?.destructured?.let { (major, minor, patch, date, sequence) ->
    PassbirdVersion(
        baseVersion = SemanticVersion(major.toInt(), minor.toInt(), patch.toInt()),
        devVersion = date.takeIf(String::isNotEmpty)?.let { DevVersion(it, sequence.toInt()) },
    )
}
fun downloadLatestJar(latestRelease: PassbirdRelease, file: Path) = urlAsStream(latestRelease.downloadUrl).use { Files.copy(it, file) }
fun checksums(latestRelease: PassbirdRelease, file: Path) = Pair(
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
    paths.map { it.fileName.toString() }.toList().sortedBy(::toPassbirdVersion)
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
fun toPassbirdVersion(fileName: String) = PASSBIRD_JAR_PATTERN.matchEntire(fileName)?.groupValues?.get(1)?.let(::parsePassbirdVersion)
    ?: error("Passbird jar filename does not contain a supported version: $fileName")
fun printwt(text: String) = print("$TAB$text")
fun printlnwt(text: String) = printwt("$text\n")
