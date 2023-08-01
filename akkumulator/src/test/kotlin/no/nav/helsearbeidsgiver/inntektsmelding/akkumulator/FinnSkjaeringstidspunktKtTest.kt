package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.trenger.finnSkjaeringstidspunkt
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.time.LocalDate

class FinnSkjaeringstidspunktKtTest : FunSpec({

    context(::finnSkjaeringstidspunkt.name) {
        withData(
            mapOf(
                "ingen perioder gir 'null'" to TestData(
                    perioder = emptyList(),
                    expectedSkjaeringstidspunkt = null
                ),

                "velger skjæringstidspunkt fra eneste periode" to TestData(
                    perioder = listOf(
                        22.mars til 26.mars
                    ),
                    expectedSkjaeringstidspunkt = 22.mars
                ),

                "slår sammen perioder som er (direkte) sammenhengende" to TestData(
                    perioder = listOf(
                        22.februar til 28.februar,
                        1.april til 6.april,
                        7.april til 19.april
                    ),
                    expectedSkjaeringstidspunkt = 1.april
                ),

                "slår sammen perioder som er sammenhengende over helg" to TestData(
                    perioder = listOf(
                        3.september til 14.september,
                        17.september til 19.september
                    ),
                    expectedSkjaeringstidspunkt = 3.september
                ),

                "benytte nyeste ved flere sykmeldingsperioder" to TestData(
                    perioder = listOf(
                        1.januar til 20.januar,
                        21.januar til 31.januar,
                        5.februar til 20.februar
                    ),
                    expectedSkjaeringstidspunkt = 5.februar
                ),

                "tåler usorterte perioder" to TestData(
                    perioder = listOf(
                        4.august til 24.august,
                        13.mai til 16.mai
                    ),
                    expectedSkjaeringstidspunkt = 4.august
                )
            )
        ) {
            val actualSkjaeringstidspunkt = finnSkjaeringstidspunkt(it.perioder)

            actualSkjaeringstidspunkt shouldBe it.expectedSkjaeringstidspunkt
        }
    }
})

private data class TestData(
    val perioder: List<Periode>,
    val expectedSkjaeringstidspunkt: LocalDate?
)
