package de.pflugradts.passbirdupdater

import java.io.BufferedInputStream
import java.math.BigInteger
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlin.system.exitProcess
import kotlin.text.Charsets.UTF_8

const val TAB = "\t"

const val LOGO_START = """
$TAB  -,"
$TAB ( '<    Passbird Updater
$TAB / ) )
"""

const val DISCLAIMER = """
${TAB}DISCLAIMER ON

${TAB}Passbird Updater is not an official part of Passbird.
${TAB}Downloading a file from the internet is a security risk.
${TAB}Make sure the domain 'pflugradts.de' which hosts Passbird
${TAB}is resolved correctly from your computer and points to the
${TAB}official and trusted Passbird website.

${TAB}DISCLAIMER OFF
"""

const val GENERIC_ERROR = """
${TAB}If the latest Passbird version couldn't be downloaded,
${TAB}the website might be temporarily offline or someone made a mistake.

${TAB}If the problem persists check the GitHub repository for
${TAB}a new version of the Passbird Updater or open an issue there:

${TAB}https://github.com/christianpflugradt/passbird-updater
"""

const val LOGO_END = """
$TAB                  ,-
${TAB}chirp chirirp    >' )
$TAB                 ( ( \
"""

const val BASEURL = "https://pflugradts.de/downloads/passbird"

fun main(args: Array<String>) {
    checkPassbirdDirectory(args)
    val versionsToKeep = args.getOrNull(1)?.toIntOrNull() ?: 3
    val passbirdDirectory = args[0]
    println(LOGO_START)
    println(DISCLAIMER)
    printlnwt("received passbird directory: $passbirdDirectory")
    printwt("determining latest version... ")
    try {
        val latest = retrieveLatestVersion()
        println(latest)
        val passbirdJarFile = Paths.get(passbirdDirectory, "passbird-$latest.jar")
        if (Files.exists(passbirdJarFile)) {
            printlnwt("version $latest already downloaded")
        } else {
            printwt("downloading version $latest... ")
            downloadLatestJar(latest, passbirdJarFile)
            println("done")
            checksums(latest, passbirdJarFile).let {
                if (it.first == it.second) {
                    Files.copy(passbirdJarFile, Paths.get(passbirdDirectory, "passbird.jar"), REPLACE_EXISTING)
                    printlnwt("updated passbird.jar to latest version $latest")
                    deleteOldJars(passbirdDirectory, findLocalJars(passbirdDirectory), versionsToKeep)
                } else {
                    printlnwt("version $latest doesn't have the expected checksum")
                    printlnwt("expected checksum: ${it.first}")
                    printlnwt("actual   checksum: ${it.second}")
                    printwt("downloaded version $latest will be removed... ")
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
fun urlAsStream(url: String) = BufferedInputStream(URL(url).openStream())
fun retrieveLatestVersion() = urlAsStream("$BASEURL/latest.txt").readAllBytes().toString(UTF_8).trimEnd('\n')
fun downloadLatestJar(latest: String, file: Path) = urlAsStream("$BASEURL/passbird-$latest.jar").use { Files.copy(it, file) }
fun checksums(latest: String, file: Path) = Pair(
    urlAsStream("$BASEURL/$latest.txt").readAllBytes().toString(Charsets.UTF_8).trimEnd('\n'),
    BigInteger(1, MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file))).toString(16).padStart(32, '0'),
)
fun findLocalJars(passbirdDirectory: String): List<String> = Files.find(Paths.get(passbirdDirectory), 1, { path, _ ->
    path.fileName.toString().matches("""passbird-\d\.\d\.\d\.jar""".toRegex())
} ).map { it.fileName.toString() }.sorted().toList()
fun deleteOldJars(passbirdDirectory: String, jars: List<String>, versionsToKeep: Int) = jars.let {
    if (it.size > versionsToKeep) {
        it.subList(0, it.size - versionsToKeep).forEach { oldJar ->
            printwt("deleting old version '$oldJar'... ")
            println("done")
            Files.delete(Paths.get(passbirdDirectory, oldJar))
        }
    }
}
fun printwt(text: String) = print("$TAB$text")
fun printlnwt(text: String) = printwt("$text\n")
