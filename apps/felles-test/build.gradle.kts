val kotestVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project
val rapidsAndRiversVersion: String by project
val utilsVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("io.ktor:ktor-client-core:$ktorVersion")

    implementation("io.kotest:kotest-assertions-core:$kotestVersion")
    implementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    implementation("io.mockk:mockk:$mockkVersion")

    implementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
}
