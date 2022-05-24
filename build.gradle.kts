import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val mockkVersion: String by project
val nimbusJoseJwtVersion: String by project
val helsearbeidsgiverTokenproviderVersion: String by project

val githubPassword: String by project

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
}

group = "no.nav.helsearbeidsgiver"
version = "0.1.0"

application {
    mainClass.set("no.nav.helsearbeidsgiver.inntektsmelding.ApplicationKt")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
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
            password = System.getenv("GITHUB_TOKEN") ?: githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/*")
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.0.1")
    implementation("io.ktor:ktor-server-netty-jvm:2.0.1")
    implementation("io.ktor:ktor-client-core-jvm:2.0.1")
    implementation("io.ktor:ktor-client-json-jvm:2.0.1")
    implementation("io.ktor:ktor-client-serialization-jvm:2.0.1")
    testImplementation("io.ktor:ktor-server-tests-jvm:2.0.1")
    implementation("io.ktor:ktor-serialization-gson:2.0.1")
    implementation("io.ktor:ktor-serialization-jackson:2.0.1")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    implementation("no.nav.helsearbeidsgiver:helsearbeidsgiver-tokenprovider:$helsearbeidsgiverTokenproviderVersion")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock-jvm:2.0.1")
}

