val brregKlientVersion = project.property("brregKlientVersion") as String

dependencies {
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:brreg-client:$brregKlientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
