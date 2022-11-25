val mockkVersion: String by project
val rapidsAndRiversVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    implementation("io.mockk:mockk:$mockkVersion")
}
