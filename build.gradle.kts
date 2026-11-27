import java.net.URI
import java.security.MessageDigest

plugins { java }

group = "com.mira"
version = "0.3.20-1.18.2"

repositories {
    mavenCentral()
    // Fallback for Kyori examination artifacts when Maven Central is intermittently unavailable.
    maven("https://repo.loohpjames.com/repository/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

val miraCoreVersion = "0.6.1-1.18.2"
val miraCoreSha256 = "8abf9f26ea889ffe1648c6f35fcb7b20eef4decd870e8385fb1603120f363b95"
val miraCoreJar = layout.projectDirectory.file("libs/MiraCore_1.18.2.jar").asFile
val miraSpawnersVersion = "0.1.16-1.18.2"
val miraSpawnersSha256 = "175b1412336f41b1df3e3d513a0eb56278a72d5ff54f59c6ace9ef3a10e6d85a"
val miraSpawnersJar = layout.projectDirectory.file("libs/MiraSpawners_1.18.2.jar").asFile

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
        downloadVerified("https://github.com/FiveSOCE/Mira-core/releases/download/dev-1.18.2/MiraCore_1.18.2.jar", miraCoreJar, miraCoreSha256)
        downloadVerified("https://github.com/FiveSOCE/Mira-Spawners/releases/download/dev-1.18.2/MiraSpawners_1.18.2.jar", miraSpawnersJar, miraSpawnersSha256)
    }
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.18.2-R0.1-SNAPSHOT")
val compileJavaVersion = providers.gradleProperty("compileJavaVersion").map(String::toInt).orElse(17)
val bytecodeJavaVersion = providers.gradleProperty("bytecodeJavaVersion").map(String::toInt).orElse(17)

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files(miraCoreJar))
    compileOnly(files(miraSpawnersJar))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(compileJavaVersion.get())) }

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadMiraDependencies)
    options.encoding = "UTF-8"
    options.release.set(bytecodeJavaVersion.get())
}

tasks.test { useJUnitPlatform() }

tasks.jar { archiveFileName.set("MiraCrates_1.18.2.jar") }


tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
