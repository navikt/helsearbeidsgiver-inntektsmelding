val pdlKlientVersion = project.property("pdlKlientVersion") as String

dependencies {
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlKlientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
