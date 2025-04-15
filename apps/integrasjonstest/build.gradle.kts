import java.util.Properties

val apps =
    setOf(
        "aareg",
        "aktiveorgnrservice",
        "altinn",
        "api",
        "berik-inntektsmelding-service",
        "bro-spinn",
        "brreg",
        "db",
        "distribusjon",
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

val aaregClientVersion: String by props
val altinnClientVersion: String by props
val arbeidsgiverNotifikasjonKlientVersion: String by props
val bakgrunnsjobbVersion: String by props
val brregKlientVersion: String by props
val dokarkivKlientVersion: String by props
val inntektKlientVersion: String by props
val pdlKlientVersion: String by props

val junitJupiterVersion: String by project
val testcontainersRedisJunitVersion: String by project
val testcontainersVersion: String by project

dependencies {
    apps.forEach {
        testImplementation(project(":$it"))
    }

    testImplementation(project(":felles-db-exposed"))
    testImplementation(testFixtures(project(":felles-db-exposed")))

    // Klienter
    testImplementation("no.nav.helsearbeidsgiver:aareg-client:$aaregClientVersion")
    testImplementation("no.nav.helsearbeidsgiver:altinn-client:$altinnClientVersion")
    testImplementation("no.nav.helsearbeidsgiver:arbeidsgiver-notifikasjon-klient:$arbeidsgiverNotifikasjonKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:brreg-client:$brregKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:dokarkiv-client:$dokarkivKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:inntekt-klient:$inntektKlientVersion")
    testImplementation("no.nav.helsearbeidsgiver:pdl-client:$pdlKlientVersion")

    testImplementation("com.redis.testcontainers:testcontainers-redis-junit:$testcontainersRedisJunitVersion")
    testImplementation("no.nav.helsearbeidsgiver:hag-bakgrunnsjobb:$bakgrunnsjobbVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}
