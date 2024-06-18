package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.time.LocalDate
import java.time.LocalDateTime

class ArbeidsforholdInnenforPeriodeKtTest : FunSpec({

    val arbeidsgiver1 = Arbeidsgiver("ORG", "123456789")
    val arbeidsgiver2 = Arbeidsgiver("ORG", "987654321")
    val arbeidsgiver3 = Arbeidsgiver("ORG", "000000001")
    val minDate = LocalDateTime.MIN

    test("Evig arbeidsforhold støttes") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(LocalDate.MIN, LocalDate.MAX)),
                registrert = minDate
            )
        )
        listOf(Periode(1.januar, 1.januar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
    }
    test("Arbeidsforhold uten start og sluttdato støttes") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(LocalDate.now().minusDays(1), null)),
                registrert = minDate
            )
        )
        listOf(Periode(1.januar, 1.januar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
    }
    test("Avsluttede og fremtidige arbeidsforhold skal ikke fjernes") {
        val today = LocalDate.now()
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(today.minusDays(1), null)),
                registrert = minDate
            )
        )
        listOf(Periode(1.januar, 1.januar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe false

    }
})
