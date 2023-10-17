val lettuceVersion: String by project
val rapidsAndRiversVersion: String by project
val slf4jVersion: String by project
val tokenProviderVersion: String by project
val tokenSupportVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("no.nav.helsearbeidsgiver:tokenprovider:$tokenProviderVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")

    implementation("io.lettuce:lettuce-core:$lettuceVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")
}
