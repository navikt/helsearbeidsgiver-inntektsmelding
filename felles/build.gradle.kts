val rapidsAndRiversVersion: String by project
val slf4jVersion: String by project
val tokenClientVersion: String by project
val tokenProviderVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    api("no.nav.helsearbeidsgiver:tokenprovider:$tokenProviderVersion") {
        exclude(group= "io.ktor", module = "ktor-serialization-jackson")
        //@TODO Ekskludere hele ktor avhengighet ellers havner vi med to ktor i klasspathen
    }
    api("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("no.nav.security:token-client-core:$tokenClientVersion")
    implementation(project(":dokument"))
}
