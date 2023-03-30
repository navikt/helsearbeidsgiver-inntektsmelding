val ktorVersion: String by project
val dokarkiv_version: String by project
val hagImXmlKontraktVersion: String by project
val jacksonVersion: String by project
val jaxbAPIVersion: String by project
val jaxbRuntimeVersion: String by project
val mapstructVersion: String by project

plugins {
    kotlin("kapt")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

dependencies {
    val pdfboxVersion: String by project
    implementation(project(":dokument"))
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkiv_version")
    implementation("no.nav.helsearbeidsgiver:helsearbeidsgiver-kontrakt-inntektsmelding:$hagImXmlKontraktVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbAPIVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    kapt("org.mapstruct:mapstruct-processor:$mapstructVersion")
}
