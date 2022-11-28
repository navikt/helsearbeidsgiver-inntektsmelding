package no.nav.helsearbeidsgiver.inntektsmelding.joark

val INNTEKTMELDING_REQUEST = mapOf(
    "orgnrUnderenhet" to "abc",
    "identitetsnummer" to "123",
    "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
    "egenmeldingsperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "arbeidsgiverperioder" to listOf(
        mapOf("fom" to "2022-08-01", "tom" to "2022-08-02"),
        mapOf("fom" to "2022-08-03", "tom" to "2022-08-04")
    ),
    "bestemmendeFraværsdag" to "2022-09-05",
    "fraværsperioder" to listOf(
        mapOf("fom" to "2022-07-01", "tom" to "2022-07-02"),
        mapOf("fom" to "2022-07-03", "tom" to "2022-07-04")
    ),
    "inntekt" to mapOf(
        "bekreftet" to "true",
        "beregnetInntekt" to "25300.0",
        "endringÅrsak" to "Tariffendring",
        "manueltKorrigert" to "true"
    ),
    "fullLønnIArbeidsgiverPerioden" to mapOf(
        "utbetalerFullLønn" to "true",
        "begrunnelse" to "BeskjedGittForSent",
        "utbetalt" to "3220.0"
    ),
    "refusjon" to mapOf(
        "utbetalerHeleEllerDeler" to "true",
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
    "tidspunkt" to "22-09-05 12:13:14",
    "årsakInnsending" to "Endring",
    "identitetsnummerInnsender" to "456"
)

val INNTEKTMELDING_REQUEST_OPTIONALS = mapOf(
    "orgnrUnderenhet" to "abc",
    "identitetsnummer" to "123",
    "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
    "egenmeldingsperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "arbeidsgiverperioder" to listOf(
        mapOf("fom" to "2022-08-01", "tom" to "2022-08-02"),
        mapOf("fom" to "2022-08-03", "tom" to "2022-08-04")
    ),
    "bestemmendeFraværsdag" to "2022-09-05",
    "fraværsperioder" to listOf(
        mapOf("fom" to "2022-07-01", "tom" to "2022-07-02"),
        mapOf("fom" to "2022-07-03", "tom" to "2022-07-04")
    ),
    "inntekt" to mapOf(
        "bekreftet" to "true",
        "beregnetInntekt" to "25300.0",
        "manueltKorrigert" to "true"
    ),
    "fullLønnIArbeidsgiverPerioden" to mapOf(
        "utbetalerFullLønn" to "true"
    ),
    "refusjon" to mapOf(
        "utbetalerHeleEllerDeler" to "true"
    ),
    "naturalytelser" to listOf(
        mapOf(
            "naturalytelse" to "Bil",
            "dato" to "2022-08-08",
            "beløp" to "123"
        )
    ),
    "tidspunkt" to "22-09-05 12:13:14",
    "årsakInnsending" to "Endring",
    "identitetsnummerInnsender" to "456"
)

val INNTEKTMELDING_REQUEST_FNUTT = mapOf(
    "orgnrUnderenhet" to "abc",
    "identitetsnummer" to "123",
    "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
    "egenmeldingsperioder" to listOf(
        mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
        mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
    ),
    "arbeidsgiverperioder" to listOf(
        mapOf("fom" to "2022-08-01", "tom" to "2022-08-02"),
        mapOf("fom" to "2022-08-03", "tom" to "2022-08-04")
    ),
    "bestemmendeFraværsdag" to "2022-09-05",
    "fraværsperioder" to listOf(
        mapOf("fom" to "2022-07-01", "tom" to "2022-07-02"),
        mapOf("fom" to "2022-07-03", "tom" to "2022-07-04")
    ),
    "inntekt" to mapOf(
        "bekreftet" to "true",
        "beregnetInntekt" to "25300.0",
        "endringÅrsak" to "",
        "manueltKorrigert" to "true"
    ),
    "fullLønnIArbeidsgiverPerioden" to mapOf(
        "utbetalerFullLønn" to "true",
        "begrunnelse" to "",
        "utbetalt" to ""
    ),
    "refusjon" to mapOf(
        "utbetalerHeleEllerDeler" to "true",
        "refusjonPrMnd" to "",
        "refusjonOpphører" to ""
    ),
    "naturalytelser" to listOf(
        mapOf(
            "naturalytelse" to "Bil",
            "dato" to "2022-08-08",
            "beløp" to "123"
        )
    ),
    "tidspunkt" to "22-09-05 12:13:14",
    "årsakInnsending" to "Endring",
    "identitetsnummerInnsender" to "456"
)
