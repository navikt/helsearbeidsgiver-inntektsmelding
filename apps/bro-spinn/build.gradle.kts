val jacksonVersion: String by project
val ktorVersion: String by project
val spinnInntektsmeldingKontraktVersion: String by project

dependencies {
    implementation(project(":utils-auth"))
    implementation(project(":kontrakt-domene-inntektsmelding"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("no.nav.sykepenger.kontrakter:inntektsmelding-kontrakt:$spinnInntektsmeldingKontraktVersion")

    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}
