// RaycastedAntiESP's PacketEvents platform (AGPL-3.0-only), modified for Freeze Shield: see GrimSync
version = "0.7.2-SNAPSHOT"

dependencies {
    implementation(project(":antiesp:locatables"))
    implementation(project(":antiesp:logging"))
    implementation(project(":antiesp:core"))
    compileOnly(project(":antiesp:leafpile"))
    compileOnly(libs.packetevents.api)
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
    compileOnly("org.jetbrains:annotations:24.0.1")
}
