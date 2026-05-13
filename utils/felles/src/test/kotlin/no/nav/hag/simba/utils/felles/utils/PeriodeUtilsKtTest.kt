package no.nav.hag.simba.utils.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars

class PeriodeUtilsKtTest :
    FunSpec({

        context("Periode.overlapperMed(PeriodeAapen)") {

            test("overlapper når periodene er like") {
                val periode = Periode(fom = 1.januar, tom = 31.januar)
                val annen = PeriodeAapen(fom = 1.januar, tom = 31.januar)

                periode.overlapperMed(annen) shouldBe true
            }

            test("overlapper når periodene delvis overlapper") {
                val periode = Periode(fom = 15.januar, tom = 15.februar)
                val annen = PeriodeAapen(fom = 1.januar, tom = 31.januar)

                periode.overlapperMed(annen) shouldBe true
            }

            test("overlapper ikke når periodene ikke overlapper") {
                val periode = Periode(fom = 1.januar, tom = 31.januar)
                val annen = PeriodeAapen(fom = 1.mars, tom = 31.mars)

                periode.overlapperMed(annen) shouldBe false
            }

            test("overlapper når annen periode har tom lik null") {
                val periode = Periode(fom = 1.februar, tom = 28.februar)
                val annen = PeriodeAapen(fom = 1.januar, tom = null)

                periode.overlapperMed(annen) shouldBe true
            }

            test("overlapper ikke når perioden slutter før annen starter") {
                val periode = Periode(fom = 1.januar, tom = 31.januar)
                val annen = PeriodeAapen(fom = 1.februar, tom = null)

                periode.overlapperMed(annen) shouldBe false
            }

            test("overlapper når periodene grenser på samme dag") {
                val periode = Periode(fom = 1.januar, tom = 1.februar)
                val annen = PeriodeAapen(fom = 1.februar, tom = 28.februar)

                periode.overlapperMed(annen) shouldBe true
            }
        }

        context("Periode.overlapperMed(Ansettelsesforhold)") {

            test("overlapper når periodene er like") {
                val periode = Periode(fom = 1.januar, tom = 31.januar)
                val annen = Ansettelsesforhold(startdato = 1.januar, sluttdato = 31.januar, yrkeskode = null, yrkesbeskrivelse = null, stillingsprosent = null)

                periode.overlapperMed(annen) shouldBe true
            }

            test("overlapper når periodene delvis overlapper") {
                val periode = Periode(fom = 15.januar, tom = 15.februar)
                val annen = Ansettelsesforhold(startdato = 1.januar, sluttdato = 31.januar, yrkeskode = null, yrkesbeskrivelse = null, stillingsprosent = null)

                periode.overlapperMed(annen) shouldBe true
            }

            test("overlapper ikke når periodene ikke overlapper") {
                val periode = Periode(fom = 1.januar, tom = 31.januar)
                val annen = Ansettelsesforhold(startdato = 1.mars, sluttdato = 31.mars, yrkeskode = null, yrkesbeskrivelse = null, stillingsprosent = null)

                periode.overlapperMed(annen) shouldBe false
            }

            test("overlapper når sluttdato er null") {
                val periode = Periode(fom = 1.februar, tom = 28.februar)
                val annen = Ansettelsesforhold(startdato = 1.januar, sluttdato = null, yrkeskode = null, yrkesbeskrivelse = null, stillingsprosent = null)

                periode.overlapperMed(annen) shouldBe true
            }

            test("overlapper ikke når perioden slutter før ansettelsesforholdet starter") {
                val periode = Periode(fom = 1.januar, tom = 31.januar)
                val annen = Ansettelsesforhold(startdato = 1.februar, sluttdato = null, yrkeskode = null, yrkesbeskrivelse = null, stillingsprosent = null)

                periode.overlapperMed(annen) shouldBe false
            }

            test("overlapper når periodene grenser på samme dag") {
                val periode = Periode(fom = 1.januar, tom = 1.februar)
                val annen =
                    Ansettelsesforhold(startdato = 1.februar, sluttdato = 28.februar, yrkeskode = null, yrkesbeskrivelse = null, stillingsprosent = null)

                periode.overlapperMed(annen) shouldBe true
            }
        }
    })
