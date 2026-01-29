val pdlKlientVersion: String by project

dependencies {
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:pdl-client:$pdlKlientVersion")
}
