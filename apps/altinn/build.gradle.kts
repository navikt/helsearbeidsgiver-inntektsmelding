dependencies {
    val altinnClientVersion = project.property("altinnClientVersion") as String
    val mockwebserverVersion = project.property("mockwebserverVersion") as String

    implementation(project(":kontrakt-resultat-tilgang"))
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockwebserverVersion")
}
