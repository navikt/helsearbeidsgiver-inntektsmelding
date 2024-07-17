dependencies {
    val altinnClientVersion: String by project
    val maskinportenClientVersion: String by project
    val mockwebserverVersion: String by project
    val nimbusJoseJwtVersion: String by project

    implementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    implementation("no.nav.helsearbeidsgiver:maskinporten-client:$maskinportenClientVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockwebserverVersion")

}
