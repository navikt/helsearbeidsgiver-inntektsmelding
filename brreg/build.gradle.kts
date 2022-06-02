
val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion="1.2.11"
val nimbusJoseJwtVersion="9.22"
val tokenproviderVersion = "0.1.3"

val githubPassword: String by project


dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:9.22")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("no.nav.helsearbeidsgiver:tokenprovider:$tokenproviderVersion")
    implementation("no.nav.helsearbeidsgiver:altinn-client:0.1.9")
    implementation("org.valiktor:valiktor-core:0.12.0")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
}
