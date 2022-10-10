plugins {
    kotlin("jvm") version "1.7.0"
    id("org.jmailen.kotlinter")
}

val jvmTargetVersion: String by project
val junitJupiterVersion: String by project
val ktorVersion: String by project
val mockkVersion: String by project
val rapidsAndRiversVersion: String by project

allprojects {
    group = "no.nav.helsearbeidsgiver"
    version = properties["version"] ?: "local-build"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        val githubPassword: String by project

        maven("https://packages.confluent.io/maven/")
        maven("https://oss.sonatype.org")
        maven("https://jitpack.io")
        mavenCentral()
        maven {
            setUrl("https://maven.pkg.github.com/navikt/*")
            credentials {
                username = "x-access-token"
                password = githubPassword
            }
        }
    }

    tasks {
        compileKotlin {
            kotlinOptions.jvmTarget = jvmTargetVersion
        }

        compileTestKotlin {
            kotlinOptions.jvmTarget = jvmTargetVersion
        }

        withType<Wrapper> {
            gradleVersion = "7.4.2"
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "no.nav.helsearbeidsgiver.inntektsmelding"
    version = properties["version"] ?: "local-build"

    tasks {
        withType<Test> {
            maxHeapSize = "6G"
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }
        }
    }

    dependencies {
        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }
}
