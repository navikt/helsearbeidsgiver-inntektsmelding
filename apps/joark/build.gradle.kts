val dokarkivKlientVersion = project.property("dokarkivKlientVersion") as String
val hagImXmlKontraktVersion = project.property("hagImXmlKontraktVersion") as String
val jacksonVersion = project.property("jacksonVersion") as String
val jaxbApiVersion = project.property("jaxbApiVersion") as String
val jaxbRuntimeVersion = project.property("jaxbRuntimeVersion") as String
val pdfboxVersion = project.property("pdfboxVersion") as String

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

dependencies {
    implementation(project(":utils-auth"))
    implementation(project(":utils-rapids-and-rivers"))
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:$jaxbApiVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    implementation("no.nav.helsearbeidsgiver:helsearbeidsgiver-kontrakt-inntektsmelding:$hagImXmlKontraktVersion") {
        constraints {
            // Uten denne så får vi problemer med mocking i integrasjonstestene.
            // Synderen er trolig avhengigheten jaxb-xew-plugin, som ikke har vært oppdatert på flere år og bruker en gammel versjon av byte-buddy.
            runtimeOnly("net.bytebuddy:byte-buddy:1.17.0")
        }
    }
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")

    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation("tools.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    testImplementation("tools.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
}
