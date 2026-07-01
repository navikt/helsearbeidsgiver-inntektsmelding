val inntektKlientVersion = project.property("inntektKlientVersion") as String

dependencies {
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
}
