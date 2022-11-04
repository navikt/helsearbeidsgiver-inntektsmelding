package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.MottattPeriode
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.Syk
import no.nav.helsearbeidsgiver.felles.SykLøsning
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import java.time.LocalDate
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
        Arbeidsforhold("af-1", "Norge AS", 80f),
        Arbeidsforhold("af-2", "Norge AS", 20f)
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
        INNTEKT = InntektLøsning(Inntekt(300.0, listOf(MottattHistoriskInntekt(YearMonth.of(fra.year, fra.month), 32_000.0))))
    )
}
