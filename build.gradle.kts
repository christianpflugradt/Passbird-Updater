import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    application
    java
    jacoco
    kotlin("jvm") version "2.4.21"
    id("org.owasp.dependencycheck") version "13.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.sourceCompatibility = JavaVersion.VERSION_17
group = "de.pflugradts"

tasks.withType<Jar> {
    archiveBaseName.set("passbird-updater")
    manifest {
        attributes["Main-Class"] = "de.pflugradts.passbirdupdater.PassbirdUpdaterKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports.xml.required.set(true)
}

dependencyCheck {
    scanConfigurations = listOf("runtimeClasspath")
    failBuildOnCVSS = 11.0f
    formats = listOf("HTML", "JSON")
    nvd.apiKey = System.getenv("NVD_API_KEY")
    analyzers.apply {
        ossIndex.enabled = false
        nexus {
            enabled.set(false)
        }
        assemblyEnabled = false
        retirejs.enabled = false
    }
    data {
        directory = System.getenv("DEPENDENCY_CHECK_DATA_LOCATION") ?: ".dependency-check"
    }
}
