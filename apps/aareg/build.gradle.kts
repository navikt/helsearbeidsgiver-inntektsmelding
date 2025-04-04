val aaregClientVersion: String by project

dependencies {
    implementation(project(":felles-auth"))
    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
}
