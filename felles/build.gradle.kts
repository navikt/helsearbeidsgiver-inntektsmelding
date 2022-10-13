val rapidsAndRiversVersion: String by project
val ktorVersion: String by project
val tokenClientVersion: String by project
val tokenproviderVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("no.nav.helsearbeidsgiver:tokenprovider:$tokenproviderVersion")
    implementation("no.nav.security:token-client-core:$tokenClientVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("no.nav.helsearbeidsgiver:aareg-client:0.2.0")
}
