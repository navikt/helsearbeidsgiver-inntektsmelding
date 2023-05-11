package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import java.time.LocalDate
import java.time.LocalDateTime

object TestData {
    val validIdentitetsnummer = "20015001543"
    val notValidIdentitetsnummer = "50012001987"
    val notValidOrgNr = "123456789"
}

fun mockArbeidsforhold(): Arbeidsforhold =
    Arbeidsforhold(
        Arbeidsgiver(
            type = "Underenhet",
            organisasjonsnummer = "810007842"
        ),
        Ansettelsesperiode(
            PeriodeNullable(
                fom = LocalDate.of(2021, 1, 1),
                tom = LocalDate.of(2021, 1, 10)
            )
        ),
        registrert = LocalDateTime.of(2021, 1, 3, 6, 30, 40, 50000)
    )
