val aaregClientVersion: String by project

dependencies {
    implementation(project(":kontrakt-domene-ansettelsesforhold"))
    implementation(project(":utils-auth"))
    implementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
}
