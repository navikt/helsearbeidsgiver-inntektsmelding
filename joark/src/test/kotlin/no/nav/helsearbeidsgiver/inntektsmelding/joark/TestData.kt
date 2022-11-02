package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.felles.BehovType
import java.util.UUID

val PACKET_INVALID = mapOf(
    "@behov" to listOf(BehovType.JOURNALFOER.name),
    "@id" to UUID.randomUUID(),
    "uuid" to "uuid",
    "identitetsnummer" to "000",
    "orgnrUnderenhet" to "abc",
    "inntektsmelding" to "xyz"
)

val PACKET_VALID = mapOf(
    "@behov" to listOf(BehovType.JOURNALFOER.name),
    "@id" to UUID.randomUUID(),
    "uuid" to "uuid",
    "inntektsmelding" to mapOf(
        "identitetsnummer" to "123",
        "orgnrUnderenhet" to "abc",
        "behandlingsdagerFom" to "2022-10-01",
        "behandlingsdagerTom" to "2022-10-11",
        "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
        "egenmeldinger" to listOf(
            mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
            mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
        ),
        "bruttonInntekt" to "25300",
        "bruttoBekreftet" to "true",
        "utbetalerFull" to "true",
        "begrunnelseRedusert" to "BeskjedGittForSent",
        "utbetalerHeleEllerDeler" to "false",
        "refusjonPrMnd" to "19500",
        "opphørerKravet" to "false",
        "opphørSisteDag" to "2022-08-08",
        "naturalytelser" to listOf(
            mapOf(
                "naturalytelseKode" to "abc",
                "dato" to "2022-08-08",
                "beløp" to "123"
            )
        ),
        "bekreftOpplysninger" to "true"
    )
)
