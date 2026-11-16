import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    application
    java
    jacoco
    kotlin("jvm") version "2.4.21"
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
