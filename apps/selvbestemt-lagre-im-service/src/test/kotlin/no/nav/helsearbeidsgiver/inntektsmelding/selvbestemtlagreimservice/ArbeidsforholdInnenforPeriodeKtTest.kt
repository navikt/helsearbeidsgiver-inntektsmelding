package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.oktober

class ArbeidsforholdInnenforPeriodeKtTest :
    FunSpec({

        test("Ansatt slutter fram i tid") {
            val ansettelsesperiode =
                setOf(
                    PeriodeAapen(
                        1.juni(2020),
                        30.april(2022),
                    ),
                )

            listOf(
                Periode(
                    15.januar(2021),
                    20.januar(2021),
                ),
            ).aktivtArbeidsforholdIPeriode(ansettelsesperiode) shouldBe true
        }

        test("Perode er innenfor ansettelsesperiode") {
            val ansettelsesperiode =
                setOf(
                    PeriodeAapen(
                        2.mars(2020),
                        20.oktober(2023),
                    ),
                )

            listOf(
                Periode(
                    15.januar(2021),
                    28.januar(2021),
                ),
            ).aktivtArbeidsforholdIPeriode(ansettelsesperiode) shouldBe true
        }

        test("Sammenhengende arbeidsforhold slås sammen til en periode") {
            val ansettelsesperioder =
                setOf(
                    PeriodeAapen(
                        1.januar(2019),
                        28.februar(2021),
                    ),
                    PeriodeAapen(
                        1.mars(2021),
                        null,
                    ),
                )

            listOf(
                Periode(
                    15.januar(2021),
                    18.januar(2021),
                ),
                Periode(
                    26.februar(2021),
                    10.mars(2021),
                ),
            ).aktivtArbeidsforholdIPeriode(ansettelsesperioder) shouldBe true
        }

        test("Periode er før ansettelsesperiode har begynt") {
            val ansettelsesperiode =
                setOf(
                    PeriodeAapen(
                        3.mars(2021),
                        null,
                    ),
                )

            listOf(
                Periode(
                    1.januar(2021),
                    5.januar(2021),
                ),
            ).aktivtArbeidsforholdIPeriode(ansettelsesperiode) shouldBe false
        }

        test("Periode begynner samtidig som ansettelsesperiode") {
            val ansettelsesperiode =
                setOf(
                    PeriodeAapen(
                        5.februar(2021),
                        null,
                    ),
                )

            listOf(
                Periode(
                    5.februar(2021),
                    9.februar(2021),
                ),
            ).aktivtArbeidsforholdIPeriode(ansettelsesperiode) shouldBe true
        }

        test("Periode etter ansettelsesperiode er avsluttet") {
            val ansettelsesperiode =
                setOf(
                    PeriodeAapen(
                        13.juli(2019),
                        5.februar(2021),
                    ),
                )

            listOf(
                Periode(
                    15.mai(2021),
                    18.mai(2021),
                ),
            ).aktivtArbeidsforholdIPeriode(ansettelsesperiode) shouldBe false
        }
    })
