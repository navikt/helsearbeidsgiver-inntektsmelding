val aaregClientVersion = project.property("aaregClientVersion") as String

dependencies {
    implementation(project(":kontrakt-domene-ansettelsesforhold"))
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
