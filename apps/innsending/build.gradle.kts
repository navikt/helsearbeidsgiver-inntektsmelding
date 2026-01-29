dependencies {
    implementation(project(":kontrakt-domene-forespoersel"))
    implementation(project(":kontrakt-domene-inntektsmelding"))
    implementation(project(":kontrakt-resultat-kvittering"))
    implementation(project(":kontrakt-resultat-lagre-im"))
    implementation(project(":utils-kafka"))
    implementation(project(":utils-valkey"))

    testImplementation(testFixtures(project(":kontrakt-domene-forespoersel")))
    testImplementation(testFixtures(project(":kontrakt-domene-inntektsmelding")))
    testImplementation(testFixtures(project(":utils-valkey")))
}
