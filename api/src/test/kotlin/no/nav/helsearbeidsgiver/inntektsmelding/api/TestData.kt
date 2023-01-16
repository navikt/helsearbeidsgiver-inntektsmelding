package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

object TestData {
    val validIdentitetsnummer = "20015001543"
    val notValidIdentitetsnummer = "50012001987"
    val notValidIdentitetsnummerInvalidCheckSum2 = "20015001544"
    val validOrgNr = "123456785"
    val notValidOrgNr = "123456789"
}

fun buildResultat(): Resultat {
    val arbeidsforhold = listOf(mockArbeidsforhold())
    val fra = LocalDate.of(2022, 10, 5)
    return Resultat(
        FULLT_NAVN = NavnLøsning("Navn Navnesen"),
        VIRKSOMHET = VirksomhetLøsning("Virksomhet AS"),
        ARBEIDSFORHOLD = ArbeidsforholdLøsning(arbeidsforhold),
        INNTEKT = InntektLøsning(Inntekt(listOf(MottattHistoriskInntekt(YearMonth.of(fra.year, fra.month), 32_000.0))))
    )
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
