package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import java.time.LocalDate
import java.time.LocalDateTime

class ArbeidsforholdUtilsKtTest : FunSpec({

    val arbeidsgiver1 = Arbeidsgiver("ORG", "123456789")
    val arbeidsgiver2 = Arbeidsgiver("ORG", "987654321")
    val arbeidsgiver3 = Arbeidsgiver("ORG", "000000001")
    val minDate = LocalDateTime.MIN

    test("arbeidsforhold innenfor periode beholdes ") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(LocalDate.MIN, LocalDate.MAX)),
                registrert = minDate
            )
        )
        arbeidsforholdListe.orgnrMedHistoriskArbeidsforhold() shouldBe listOf(arbeidsgiver1.organisasjonsnummer)
    }
    test("null-verdier i fom skal ikke fjernes") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(null, null)),
                registrert = minDate
            )
        )
        arbeidsforholdListe.orgnrMedHistoriskArbeidsforhold() shouldBe listOf(arbeidsgiver1.organisasjonsnummer)
    }
    test("Avsluttede og fremtidige arbeidsforhold skal ikke fjernes") {
        val today = LocalDate.now()
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(today.minusDays(1), null)),
                registrert = minDate
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver2,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(today.minusMonths(1), today.minusDays(1))),
                registrert = minDate
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver3,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(today.plusDays(1), today.plusMonths(1))),
                registrert = minDate
            )
        )
        arbeidsforholdListe.orgnrMedHistoriskArbeidsforhold() shouldBe listOf(
            arbeidsgiver1.organisasjonsnummer,
            arbeidsgiver2.organisasjonsnummer,
            arbeidsgiver3.organisasjonsnummer
        )
    }
})
