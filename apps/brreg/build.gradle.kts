val brregKlientVersion: String by project

dependencies {
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:brreg-client:$brregKlientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
