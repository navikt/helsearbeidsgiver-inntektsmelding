import java.util.Properties

val apps =
    setOf(
        "aareg",
        "aktiveorgnrservice",
        "altinn",
        "api",
        "berik-inntektsmelding-service",
        "brreg",
        "db",
        "distribusjon",
        "faisu-service",
        "feil-behandler",
        "forespoersel-marker-besvart",
        "helsebro",
        "innsending",
        "inntekt",
        "inntekt-selvbestemt-service",
        "inntektservice",
        "joark",
        "notifikasjon",
        "pdl",
        "selvbestemt-hent-im-service",
        "selvbestemt-lagre-im-service",
        "tilgangservice",
        "trengerservice",
    )

val props = Properties()
apps
    .map { file("../$it/gradle.properties") }
    .filter { it.exists() }
    .forEach { file ->
        file.inputStream().use { props.load(it) }
    }

// Leses fra hvert prosjekt sin gradle.properties
val aaregClientVersion = props.getProperty("aaregClientVersion") as String
val altinnClientVersion = props.getProperty("altinnClientVersion") as String
val arbeidsgiverNotifikasjonKlientVersion = props.getProperty("arbeidsgiverNotifikasjonKlientVersion") as String
val bakgrunnsjobbVersion = props.getProperty("bakgrunnsjobbVersion") as String
val brregKlientVersion = props.getProperty("brregKlientVersion") as String
val dokarkivKlientVersion = props.getProperty("dokarkivKlientVersion") as String
val inntektKlientVersion = props.getProperty("inntektKlientVersion") as String
val pdlKlientVersion = props.getProperty("pdlKlientVersion") as String

// Leses fra integrasjonstest sin gradle.properties
val lettuceVersion = project.property("lettuceVersion") as String
val testcontainersRedisVersion = project.property("testcontainersRedisVersion") as String
val testcontainersVersion = project.property("testcontainersVersion") as String

dependencies {
    apps.forEach {
        testImplementation(project(":$it"))
    }

    testImplementation(project(":kontrakt-domene-ansettelsesforhold"))
    testImplementation(project(":kontrakt-domene-arbeidsgiver"))
    testImplementation(project(":kontrakt-domene-forespoersel"))
    testImplementation(project(":kontrakt-domene-inntektsmelding"))
    testImplementation(project(":kontrakt-domene-bro-forespoersel"))
    testImplementation(project(":kontrakt-resultat-forespoersel"))
    testImplementation(project(":kontrakt-resultat-tilgang"))
    testImplementation(project(":utils-db-exposed"))
    testImplementation(project(":utils-kafka"))
    testImplementation(project(":utils-rapids-and-rivers"))
    testImplementation(project(":utils-valkey"))
    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
    testImplementation(testFixtures(project(":utils-db-exposed")))
    testImplementation(testFixtures(project(":utils-kafka")))
    testImplementation(testFixtures(project(":utils-rapids-and-rivers")))
    testImplementation(testFixtures(project(":utils-valkey")))

    // Klienter
    testImplementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
    testImplementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    testImplementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:brreg-client:$brregKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:pdl-client:$pdlKlientVersion")

    testImplementation("com.redis:testcontainers-redis:$testcontainersRedisVersion")
    testImplementation("io.lettuce:lettuce-core:$lettuceVersion")
    testImplementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")
    testImplementation("org.testcontainers:testcontainers-kafka:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
}
