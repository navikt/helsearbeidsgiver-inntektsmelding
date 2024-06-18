package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.utils.test.date.februar
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
        listOf(Periode(1.januar, 16.januar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
    }
    test("Arbeidsforhold uten sluttdato støttes") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(1.januar, null)),
                registrert = minDate
            )
        )
        listOf(Periode(1.januar, 16.januar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
    }
    test("Avsluttede arbeidsforhold som dekker sykeperiode støttes") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(1.januar, 1.februar)),
                registrert = minDate
            )
        )
        listOf(Periode(1.januar, 16.januar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
    }

    test("Avsluttede arbeidsforhold som ikke dekker sykeperiode stoppes") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(1.januar, 1.februar)),
                registrert = minDate
            )
        )
        listOf(Periode(20.januar, 5.februar)).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe false
    }

    test("Slår sammen arbeidsforhold") {
        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(
                    PeriodeNullable(
                    LocalDate.of(2019, 1, 1),
                    LocalDate.of(2021, 2, 28))),
                registrert = minDate
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver1,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(
                    LocalDate.of(2021, 3, 1),
                    null
                )
                ),
                registrert = minDate
            )
        )
        listOf(
            Periode(
                LocalDate.of(2021, 1, 15),
                LocalDate.of(2021, 1, 18)
            ),
            Periode(
                LocalDate.of(2021, 2, 26),
                LocalDate.of(2021, 3, 10),
            )
        ).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
    }
})
