val pdlKlientVersion: String by project

dependencies {
    implementation(project(":felles-auth"))
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlKlientVersion")
}
