
val ktorVersion="2.0.1"
val kotlinVersion="1.6.21"
val kotlinterVersion="3.10.0"
val logbackVersion="1.2.11"
val nimbusJoseJwtVersion="9.22"
val tokenproviderVersion: String by project

repositories {
    maven {
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: "x-access-token"
            password = System.getenv("GITHUB_TOKEN")
        }
        setUrl("https://maven.pkg.github.com/navikt/*")
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:9.22")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("no.nav.helsearbeidsgiver:tokenprovider:$tokenproviderVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}
