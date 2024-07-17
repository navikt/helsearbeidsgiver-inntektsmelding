dependencies {
    val altinnClientVersion: String by project
    val maskinportenClientVersion: String by project
    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:maskinporten-client:$maskinportenClientVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.40")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.14")

}
