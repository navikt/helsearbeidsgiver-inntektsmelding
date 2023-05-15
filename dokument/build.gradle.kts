
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val githubPassword: String by project
val fabrikt: Configuration by configurations.creating

val generationDir = "$projectDir/build/generated/"
val apiFile = "$projectDir/src/main/resources/spesifikasjon.yaml"

plugins {
    id("org.jmailen.kotlinter")
}

sourceSets {
    main { kotlin.srcDirs("$generationDir/src/main/kotlin") }
    main { kotlin.srcDirs("/src/main/kotlin") }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven {
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/helsearbeidsgiver-tokenprovider")
    }
}

tasks {
    val generateCode by creating(JavaExec::class) {
        inputs.files(apiFile)
        outputs.dir(generationDir)
        outputs.cacheIf { true }
        classpath(fabrikt)
        main = "com.cjbooms.fabrikt.cli.CodeGen"
        args = listOf(
            "--output-directory", generationDir,
            "--base-package", "no.nav.helsearbeidsgiver.felles.inntektsmelding.felles",
            "--api-file", apiFile,
            "--targets", "http_models",
            "--http-client-opts", "resilience4j"
        )
    }

    register<FormatTask>("ktFormat") {
        source(files("$generationDir/src/main/kotlin"))
        dependsOn(generateCode)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        dependsOn(generateCode)
        dependsOn("ktFormat")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "no.nav.helsearbeidsgiver.inntektsmelding"
            artifactId = "dokument"
            version = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + System.getenv("GITHUB_SHA")
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/${rootProject.name}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

dependencies {
    fabrikt("com.cjbooms:fabrikt:8.1.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.14.2")
    implementation("javax.validation:validation-api:2.0.1.Final")
}
