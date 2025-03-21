package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.domene.PeriodeNullable
import java.time.LocalDate
import java.time.LocalDateTime

class ArbeidsforholdInnenforPeriodeKtTest :
    FunSpec({

        val arbeidsgiver1 = Arbeidsgiver("ORG", "123456789")
        val minDate = LocalDateTime.MIN
        test("Ansatt slutter fram i tid") {
            listOf(
                Periode(
                    LocalDate.of(2021, 1, 15),
                    LocalDate.of(2021, 1, 20),
                ),
            ).aktivtArbeidsforholdIPeriode(AaregTestData.arbeidsforholdMedSluttDato) shouldBe true
        }

        test("Perode er innenfor Arbeidsforholdet") {
            listOf(
                Periode(
                    LocalDate.of(2021, 1, 15),
                    LocalDate.of(2021, 1, 28),
                ),
            ).aktivtArbeidsforholdIPeriode(AaregTestData.evigArbeidsForholdListe) shouldBe true
        }

        test("Sammenhengende arbeidsforhold slås sammen til en periode") {
            val arbeidsforholdListe =
                listOf(
                    Arbeidsforhold(
                        arbeidsgiver = arbeidsgiver1,
                        ansettelsesperiode =
                            Ansettelsesperiode(
                                PeriodeNullable(
                                    LocalDate.of(2019, 1, 1),
                                    LocalDate.of(2021, 2, 28),
                                ),
                            ),
                        registrert = minDate,
                    ),
                    Arbeidsforhold(
                        arbeidsgiver = arbeidsgiver1,
                        ansettelsesperiode =
                            Ansettelsesperiode(
                                PeriodeNullable(
                                    LocalDate.of(2021, 3, 1),
                                    null,
                                ),
                            ),
                        registrert = minDate,
                    ),
                )
            listOf(
                Periode(
                    LocalDate.of(2021, 1, 15),
                    LocalDate.of(2021, 1, 18),
                ),
                Periode(
                    LocalDate.of(2021, 2, 26),
                    LocalDate.of(2021, 3, 10),
                ),
            ).aktivtArbeidsforholdIPeriode(arbeidsforholdListe) shouldBe true
        }

        test("Periode er før Arbeidsforholdet har begynt") {
            listOf(
                Periode(
                    LocalDate.of(2021, 1, 1),
                    LocalDate.of(2021, 1, 5),
                ),
            ).aktivtArbeidsforholdIPeriode(AaregTestData.paagaaendeArbeidsforholdListe) shouldBe false
        }

        test("Periode begynner samtidig som Arbeidsforholdet") {
            listOf(
                Periode(
                    LocalDate.of(2021, 2, 5),
                    LocalDate.of(2021, 2, 9),
                ),
            ).aktivtArbeidsforholdIPeriode(AaregTestData.paagaaendeArbeidsforholdListe) shouldBe true
        }

        test("Periode etter Arbeidsforholdet er avsluttet") {
            listOf(
                Periode(
                    LocalDate.of(2021, 5, 15),
                    LocalDate.of(2021, 5, 18),
                ),
            ).aktivtArbeidsforholdIPeriode(AaregTestData.avsluttetArbeidsforholdListe) shouldBe false
        }
    })
