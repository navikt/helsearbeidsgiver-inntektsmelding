val aaregClientVersion: String by project
val altinnClientVersion: String by project
val arbeidsgiverNotifikasjonKlientVersion: String by project
val brregKlientVersion: String by project
val dokarkivKlientVersion: String by project
val inntektKlientVersion: String by project
val junitJupiterVersion: String by project
val pdlKlientVersion: String by project
val testcontainersRedisJunitVersion: String by project
val testcontainersVersion: String by project

dependencies {
    testImplementation(project(":aareg"))
    testImplementation(project(":aktiveorgnrservice"))
    testImplementation(project(":altinn"))
    testImplementation(project(":api"))
    testImplementation(project(":bro-spinn"))
    testImplementation(project(":brreg"))
    testImplementation(project(":db"))
    testImplementation(project(":distribusjon"))
    testImplementation(project(":forespoersel-besvart"))
    testImplementation(project(":forespoersel-marker-besvart"))
    testImplementation(project(":forespoersel-mottatt"))
    testImplementation(project(":helsebro"))
    testImplementation(project(":innsending"))
    testImplementation(project(":inntekt"))
    testImplementation(project(":inntektservice"))
    testImplementation(project(":inntekt-selvbestemt-service"))
    testImplementation(project(":joark"))
    testImplementation(project(":notifikasjon"))
    testImplementation(project(":pdl"))
    testImplementation(project(":tilgangservice"))
    testImplementation(project(":trengerservice"))
    testImplementation(project(":berik-inntektsmelding-service"))

    testImplementation(project(":felles"))
    testImplementation(project(":felles-db-exposed"))

    testImplementation(testFixtures(project(":felles")))
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
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}
