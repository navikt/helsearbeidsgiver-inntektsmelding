val rapidsAndRiversVersion: String by project
val tokenClientVersion: String by project
val tokenProviderVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("no.nav.helsearbeidsgiver:tokenprovider:$tokenProviderVersion")

    implementation("no.nav.security:token-client-core:$tokenClientVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}
