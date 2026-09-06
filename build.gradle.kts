import java.net.URI
import java.security.MessageDigest

plugins { java }

group = "com.mira"
version = "0.3.14"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

val miraCoreVersion = "0.4.1"
val miraCoreSha256 = "4a20f538762bb550b4f8c359eb16945eee786ed0741ba60c0dbfc7e07e2249a9"
val miraCoreJar = layout.projectDirectory.file("libs/MiraCore-$miraCoreVersion.jar").asFile
val miraSpawnersVersion = "0.1.9"
val miraSpawnersSha256 = "47a65e6eb8fad6ed7886eaf156d16f21e20af7fc3cb6ef955f1cb87eda001a5f"
val miraSpawnersJar = layout.projectDirectory.file("libs/MiraSpawners-$miraSpawnersVersion.jar").asFile

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
}

fun downloadVerified(url: String, target: File, expectedSha256: String) {
    if (target.exists() && sha256(target) == expectedSha256) return
    target.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
    check(sha256(target) == expectedSha256) { "Downloaded dependency failed SHA-256 verification: ${target.name}" }
}

val downloadMiraDependencies by tasks.registering {
    doLast {
        downloadVerified("https://github.com/FiveSOCE/MIra-core/releases/download/v$miraCoreVersion/MiraCore-$miraCoreVersion.jar", miraCoreJar, miraCoreSha256)
        downloadVerified("https://github.com/FiveSOCE/Mira-Spawners/releases/download/v$miraSpawnersVersion/MiraSpawners-$miraSpawnersVersion.jar", miraSpawnersJar, miraSpawnersSha256)
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files(miraCoreJar))
    compileOnly(files(miraSpawnersJar))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadMiraDependencies)
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test { useJUnitPlatform() }

tasks.jar { archiveFileName.set("MiraCrates-${project.version}.jar") }
