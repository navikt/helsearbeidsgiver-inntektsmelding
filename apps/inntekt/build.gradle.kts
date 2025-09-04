val inntektKlientVersion: String by project

dependencies {
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")
}
