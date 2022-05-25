
val ktorVersion="2.0.1"
val kotlinVersion="1.6.21"
val kotlinterVersion="3.10.0"
val logbackVersion="1.2.11"
val nimbusJoseJwtVersion="9.22"
val helsearbeidsgiverTokenproviderVersion="0.1.3"

group = "no.nav.helsearbeidsgiver"
version = "0.1.0"

plugins {
    kotlin("jvm") version "1.6.21"
    application
}

application {
    mainClass.set("no.nav.helsearbeidsgiver.inntektsmelding.ApplicationKt")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
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
    implementation("no.nav.helsearbeidsgiver:tokenprovider:$helsearbeidsgiverTokenproviderVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
}
