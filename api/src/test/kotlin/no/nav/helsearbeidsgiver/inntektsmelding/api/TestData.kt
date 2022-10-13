package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.*
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
    val arbeidsforhold = listOf(
        Arbeidsforhold(
            Arbeidsgiver(
                type = "Underenhet",
                organisasjonsnummer = "810007842"
            ),
            Ansettelsesperiode(
                Periode(
                    fom = LocalDate.of(2021, 1, 1),
                    tom = LocalDate.of(2021, 1, 10)
                )
            ),
            registrert = LocalDateTime.of(2021, 1, 3, 6, 30, 40, 50000)
        )
    )
    val fra = LocalDate.of(2022, 10, 5)
    val fravaersperiode = mutableMapOf<String, List<MottattPeriode>>()
    fravaersperiode.put(TestData.validIdentitetsnummer, listOf(MottattPeriode(fra, fra.plusDays(10))))
    val behandlingsperiode = MottattPeriode(fra, fra.plusDays(10))
    return Resultat(
        FULLT_NAVN = NavnLøsning("Navn Navnesen"),
        SYK = SykLøsning(Syk(fravaersperiode, behandlingsperiode)),
        VIRKSOMHET = VirksomhetLøsning("Virksomhet AS"),
        ARBEIDSFORHOLD = ArbeidsforholdLøsning(arbeidsforhold),
        INNTEKT = InntektLøsning(Inntekt(300.0, listOf(MottattHistoriskInntekt(YearMonth.of(fra.year, fra.month), 32000.0))))
    )
}
