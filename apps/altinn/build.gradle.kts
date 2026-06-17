dependencies {
    val altinnClientVersion: String by project
    val mockwebserverVersion: String by project

    implementation(project(":kontrakt-resultat-tilgang"))
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockwebserverVersion")
}
