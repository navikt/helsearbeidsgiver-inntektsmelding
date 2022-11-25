package no.nav.helsearbeidsgiver.inntektsmelding.joark

val IM_VALID = mapOf(
    "orgnrUnderenhet" to "abc",
    "identitetsnummer" to "123",
    "fulltNavn" to "Ola Normann",
    "virksomhetNavn" to "Norge AS",
    "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
    "fraværsperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "arbeidsgiverperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "bestemmendeFraværsdag" to "2022-09-05",
    "egenmeldingsperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "beregnetInntekt" to "25300",
    "beregnetInntektEndringÅrsak" to "Tariffendring",
    "fullLønnIArbeidsgiverPerioden" to mapOf(
        "utbetalerFullLønn" to "true",
        "begrunnelse" to "BeskjedGittForSent"
    ),
    "refusjon" to mapOf(
        "refusjonPrMnd" to "123123",
        "refusjonOpphører" to "2022-09-06"
    ),
    "naturalytelser" to listOf(
        mapOf(
            "naturalytelse" to "Bil",
            "dato" to "2022-08-08",
            "beløp" to "123"
        )
    ),
    "årsakInnsending" to "Endring",
    "identitetsnummerInnsender" to "456"
)
