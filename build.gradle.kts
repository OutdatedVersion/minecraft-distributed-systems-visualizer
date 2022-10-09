import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    `java-library`
}

group = "wtf.bens"
version = "0.1.0"

repositories {
    mavenCentral()
    maven(url = "https://papermc.io/repo/repository/maven-public/")
    maven(url = "https://repo.aikar.co/content/groups/aikar/")
}

dependencies {
    implementation("co.aikar:acf-paper:0.5.0-SNAPSHOT")
    implementation("com.elmakers.mine.bukkit:EffectLib:9.4")
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
}

// from https://discuss.gradle.org/t/how-to-run-execute-string-as-a-shell-command-in-kotlin-dsl/32235/10
fun runCommand(cmd: String, currentWorkingDir: File = file("./")): String {
    val stdout = org.apache.commons.io.output.ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = cmd.split("\\s".toRegex())
        standardOutput = stdout
    }
    return String(stdout.toByteArray()).trim()
}

tasks.withType<ProcessResources> {
    expand(
        "main" to "wtf.bens.minecraft.mspatterns.Plugin",
        "version" to "${project.version}-${runCommand("git rev-parse --short HEAD")}-${runCommand("git rev-parse --abbrev-ref HEAD")}"
    )
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.javaParameters = true
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("patterns-visualizer-${project.version}-shadow.jar")
}