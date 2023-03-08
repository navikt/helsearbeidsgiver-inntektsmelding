import java.time.LocalDate
import java.time.format.DateTimeFormatter

val ktorVersion: String by project
val githubPassword: String by project

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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
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


