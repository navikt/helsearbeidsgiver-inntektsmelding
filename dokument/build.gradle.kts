val ktorVersion: String by project
val githubPassword: String by project

plugins {
    id("maven-publish")
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId="no.nav.helsearbeidsgiver.inntektsmelding"
            artifactId="dokument"
            version=project.version.toString()
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
    implementation(project(":felles"))
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
}
