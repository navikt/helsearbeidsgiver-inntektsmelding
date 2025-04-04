val inntektKlientVersion: String by project

dependencies {
    implementation(project(":felles-auth"))
    implementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")
}
