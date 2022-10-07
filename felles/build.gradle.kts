val rapidsAndRiversVersion: String by project
val ktorVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
}
