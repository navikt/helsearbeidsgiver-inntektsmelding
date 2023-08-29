val ktorVersion: String by project
val spinnInntektsmeldingKontraktVersion: String by project

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("no.nav.sykepenger.kontrakter:inntektsmelding-kontrakt:$spinnInntektsmeldingKontraktVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}
