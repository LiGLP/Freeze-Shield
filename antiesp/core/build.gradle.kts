// RaycastedAntiESP core (AGPL-3.0-only)
version = "0.7.2-SNAPSHOT"

dependencies {
    compileOnly("org.spongepowered:configurate-core:4.2.0")
    compileOnly(files("../libs/yaml-4.3.0-SNAPSHOT-cubi.jar"))
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
    compileOnly("org.jetbrains:annotations:24.0.1")
    implementation(project(":antiesp:locatables"))
    compileOnly(project(":antiesp:leafpile"))
    compileOnly(project(":antiesp:logging"))
}
