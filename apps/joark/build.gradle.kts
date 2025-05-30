val dokarkivKlientVersion: String by project
val hagImXmlKontraktVersion: String by project
val jacksonVersion: String by project
val jaxbAPIVersion: String by project
val jaxbRuntimeVersion: String by project
val pdfboxVersion: String by project

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

dependencies {
    implementation(project(":felles-auth"))

    implementation("javax.xml.bind:jaxb-api:$jaxbAPIVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    implementation("no.nav.helsearbeidsgiver:helsearbeidsgiver-kontrakt-inntektsmelding:$hagImXmlKontraktVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")

    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    testImplementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
}
