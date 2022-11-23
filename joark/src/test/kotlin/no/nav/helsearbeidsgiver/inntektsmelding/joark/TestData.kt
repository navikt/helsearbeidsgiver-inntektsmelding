package no.nav.helsearbeidsgiver.inntektsmelding.joark

val IM_VALID = mapOf(
    "orgnrUnderenhet" to "abc",
    "identitetsnummer" to "123",
    "fulltNavn" to "Ola Normann",
    "virksomhetNavn" to "Norge AS",
    "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
    "egenmeldingsperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "bruttoInntekt" to mapOf(
        "bruttoInntekt" to "25300",
        "bekreftet" to "true",
        "endringaarsak" to "Årsak",
        "manueltKorrigert" to "true"
    ),
    "fullLønnIArbeidsgiverPerioden" to mapOf(
        "utbetalerFullLønn" to "true",
        "begrunnelse" to "BeskjedGittForSent"
    ),
    "heleEllerdeler" to mapOf(
        "utbetalerHeleEllerDeler" to "true",
        "refusjonPrMnd" to "123123",
        "opphørSisteDag" to "2022-09-06"
    ),
    "naturalytelser" to listOf(
        mapOf(
            "naturalytelseKode" to "abc",
            "dato" to "2022-08-08",
            "beløp" to "123"
        )
    ),
    "bekreftOpplysninger" to "true"
)
