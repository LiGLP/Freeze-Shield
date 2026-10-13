// Embedded RaycastedAntiESP (AGPL-3.0-only, https://github.com/Cubicake/RaycastedAntiESP).
// Built into a separate jar that Freeze Shield's Bukkit jar carries and loads on Paper 1.21.4+.
// The upstream commits these sources come from are listed in UPSTREAM-VERSION.txt.

val upstreamCommit = "d90ac06a631d8b62cd730ac1ad8c329796081a3e"

subprojects {
    apply(plugin = "java-library")

    repositories {
        maven("https://maven.grim.ac/public/releases") {
            content { includeGroup("com.github.retrooper") }
        }
        maven("https://maven.grim.ac/public/snapshots") {
            content { includeGroup("com.github.retrooper") }
        }
        maven("https://repo.grim.ac/snapshots") {
            content { includeGroup("com.github.retrooper") }
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroup("io.papermc.paper")
                includeGroup("net.md-5")
                includeGroup("com.mojang")
            }
        }
        maven("https://repo.fancyinnovations.com/releases") {
            content { includeGroup("de.oliver") }
        }
        maven("https://eldonexus.de/repository/maven-public/") {
            content {
                includeGroup("de.oliver")
                includeGroupByRegex("net\\.strokkur.*")
            }
        }
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    // Fills the build-properties/*.yml files the anti-ESP reads its version from
    tasks.named<ProcessResources>("processResources") {
        val moduleVersion = providers.provider { project.version.toString() }
        inputs.property("version", moduleVersion)
        filesMatching("build-properties/*.yml") {
            expand(
                mapOf(
                    "short_git" to upstreamCommit.substring(0, 8),
                    "long_git" to upstreamCommit,
                    "build_time" to "freezeshield",
                    "version" to moduleVersion.get(),
                )
            )
        }
    }
}
