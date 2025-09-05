dependencies {
    val altinnClientVersion: String by project
    val mockwebserverVersion: String by project

    implementation(project(":kontrakt-resultat-tilgang"))
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")

    testImplementation("com.squareup.okhttp3:mockwebserver:$mockwebserverVersion")
}
