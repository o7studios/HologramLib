project.version = "1.8.2"

group = "com.github.max1mde"
version = "1.8.7"

plugins {
    kotlin("jvm") version "2.1.10"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("com.gradleup.shadow") version "9.4.1"
    id("maven-publish")
}


publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
    // HeldenUI comes from "gradlew publishToMavenLocal" in project-interface/plugin.
    mavenLocal { content { includeModule("studio.o7", "heldenui") } }
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }
    maven("https://maven.pvphub.me/tofaa")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    compileOnly("studio.o7:megalodon-api:0.0.16")
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.1")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.arcaniax:HeadDatabase-API:1.3.2")
    compileOnly("studio.o7:heldenui:0.1.0-SNAPSHOT")

    implementation("io.github.tofaa2:spigot:3.0.3-SNAPSHOT")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("com.tcoded:FoliaLib:0.5.1")

    implementation("com.github.HologramLib:AddonLib:1.1.0")

    library(kotlin("stdlib"))
    library(kotlin("reflect"))
}

kotlin {
    jvmToolchain(25)
}

tasks.compileKotlin {
    compilerOptions.javaParameters = true
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val pluginPackage = "com.maximde.hologramlib"
tasks.shadowJar {

    archiveFileName.set("HologramLib-$version.jar")

    exclude(
        "DebugProbesKt.bin",
        "*.SF", "*.DSA", "*.RSA", "META-INF/**", "OSGI-INF/**",
        "deprecated.properties", "driver.properties", "mariadb.properties", "mozilla/public-suffix-list.txt",
        "org/slf4j/**", "org/apache/logging/slf4j/**", "org/apache/logging/log4j/**", "Log4j-*"
    )

    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains.kotlinx:.*"))
        exclude(dependency("org.checkerframework:.*"))
        exclude(dependency("org.jetbrains:annotations"))
        exclude(dependency("org.slf4j:.*"))
    }

    rootDir.resolve("gradle").resolve("relocations.txt").readLines().forEach {
        if (it.isNotBlank()) relocate(it, "$pluginPackage.__relocated__.$it")
    }
}

bukkit {
    version = project.version.toString()
    main = "com.maximde.hologramlib.Main"
    apiVersion = "1.19"
    author = "MaximDe"
    foliaSupported = true
    depend = listOf("packetevents")
    softDepend = listOf("ItemsAdder", "PlaceholderAPI", "HeadDatabase", "megalodon", "HeldenUI")
    name = "HologramLib"
}