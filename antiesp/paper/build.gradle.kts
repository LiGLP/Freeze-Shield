import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import versioning.BuildConfig

// RaycastedAntiESP's Paper platform (AGPL-3.0-only), modified to run inside Freeze Shield.
// Produces freezeshield-antiesp.jar, which the Bukkit module embeds and loads with its own classloader.

plugins {
    id("com.gradleup.shadow")
}

version = "0.10.2-SNAPSHOT"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(libs.packetevents.spigot)
    compileOnly("org.spongepowered:configurate-core:4.2.0")
    implementation(files("../libs/yaml-4.3.0-SNAPSHOT-cubi.jar"))

    compileOnly("de.oliver:FancyHolograms:2.9.1")
    compileOnly("de.oliver:FancyNpcs:2.9.2")

    compileOnly("net.strokkur.commands:annotations-paper:2.3.0")
    annotationProcessor("net.strokkur.commands:processor-paper:2.3.0")

    compileOnly("org.jetbrains:annotations:24.0.1")

    implementation(project(":antiesp:leafpile"))
    implementation(project(":antiesp:locatables"))
    implementation(project(":antiesp:logging"))
    implementation(project(":antiesp:core"))
    implementation(project(":antiesp:packetevents"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("freezeshield-antiesp.jar")

    relocate("ca.spottedleaf", "games.cubi.libs.raycastedantiesp.spottedleaf")
    relocate("org.spongepowered.configurate.yaml", "games.cubi.libs.raycastedantiesp.configurate.yaml")

    // Use the PacketEvents copy Freeze Shield bundles. Adventure, fastutil etc. are left alone on purpose,
    // the anti-ESP uses the server's own copies of those, not Freeze Shield's relocated ones.
    if (BuildConfig.shadePE && BuildConfig.relocate) {
        relocate("com.github.retrooper.packetevents", "ac.grim.grimac.shaded.com.github.retrooper.packetevents")
        relocate("io.github.retrooper.packetevents", "ac.grim.grimac.shaded.io.github.retrooper.packetevents")
    }
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
