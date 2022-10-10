plugins {
    kotlin("jvm") version "1.7.0"
}

val jacksonVersion = "2.13.4"
val logbackClassicVersion = "1.2.11"
val logstashVersion = "7.2"
val jvmTargetVersion = "17"

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

    /*
        avhengigheter man legger til her blir lagt på -alle- prosjekter.
        med mindre alle submodulene (modellen, apiet, jobs, osv) har behov for samme avhengighet,
        bør det heller legges til de enkelte som har behov.
        Dersom det er flere som har behov så kan det være lurt å legge avhengigheten til
         dependencyResolutionManagement i settings.gradle.kts
     */
    dependencies {

        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
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
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }
}
