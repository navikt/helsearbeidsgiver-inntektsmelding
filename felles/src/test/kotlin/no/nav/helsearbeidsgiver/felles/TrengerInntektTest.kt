package no.nav.helsearbeidsgiver.felles

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.time.LocalDate

class TrengerInntektTest : FunSpec({

    context(TrengerInntekt::bestemmendeFravaersdag.name) {
        withData(
            mapOf(
                "velger bestemmende fraværsdag fra eneste egenmeldingsperiode" to TestData(
                    egenmeldingsperioder = listOf(
                        10.mars til 13.mars
                    ),
                    sykmeldingsperioder = emptyList(),
                    expectedBestemmendeFravaersdag = 10.mars
                ),
                "velger bestemmende fraværsdag fra eneste sykmeldingsperiode" to TestData(
                    egenmeldingsperioder = emptyList(),
                    sykmeldingsperioder = listOf(
                        22.mars til 26.mars
                    ),
                    expectedBestemmendeFravaersdag = 22.mars
                ),

                "slår sammen perioder som er (direkte) sammenhengende" to TestData(
                    egenmeldingsperioder = listOf(
                        19.februar til 20.februar
                    ),
                    sykmeldingsperioder = listOf(
                        22.februar til 28.februar,
                        1.april til 6.april,
                        7.april til 19.april
                    ),
                    expectedBestemmendeFravaersdag = 1.april
                ),

                "slår sammen perioder som er sammenhengende over helg" to TestData(
                    egenmeldingsperioder = emptyList(),
                    sykmeldingsperioder = listOf(
                        3.september til 14.september,
                        17.september til 19.september
                    ),
                    expectedBestemmendeFravaersdag = 3.september
                ),

                "slår sammen sykmeldingsperioder med egenmeldingsperioder" to TestData(
                    egenmeldingsperioder = listOf(
                        2.mai til 3.mai
                    ),
                    sykmeldingsperioder = listOf(
                        4.mai til 10.mai
                    ),
                    expectedBestemmendeFravaersdag = 2.mai
                ),

                "slår sammen vekslende egenmeldings- og sykmeldingsperioder" to TestData(
                    egenmeldingsperioder = listOf(
                        25.oktober til 29.oktober,
                        6.november til 11.november
                    ),
                    sykmeldingsperioder = listOf(
                        30.oktober til 5.november,
                        12.november til 17.november
                    ),
                    expectedBestemmendeFravaersdag = 25.oktober
                ),

                "benytte nyeste ved flere sykmeldingsperioder" to TestData(
                    egenmeldingsperioder = emptyList(),
                    sykmeldingsperioder = listOf(
                        1.januar til 20.januar,
                        21.januar til 31.januar,
                        5.februar til 20.februar
                    ),
                    expectedBestemmendeFravaersdag = 5.februar
                ),

                "benytt nyeste i vekslende egenmeldings- og sykmeldingsperioder" to TestData(
                    egenmeldingsperioder = listOf(
                        13.juli til 19.juli,
                        31.juli til 7.august
                    ),
                    sykmeldingsperioder = listOf(
                        20.juli til 25.juli,
                        8.august til 15.august
                    ),
                    expectedBestemmendeFravaersdag = 31.juli
                ),

                "tåler usorterte perioder" to TestData(
                    egenmeldingsperioder = emptyList(),
                    sykmeldingsperioder = listOf(
                        4.august til 24.august,
                        13.mai til 16.mai
                    ),
                    expectedBestemmendeFravaersdag = 4.august
                )
            )
        ) {
            val forespoersel = mockTrengerInntekt().copy(
                egenmeldingsperioder = it.egenmeldingsperioder,
                sykmeldingsperioder = it.sykmeldingsperioder,
                bestemmendeFravaersdager = emptyMap()
            )

            val actualBestemmendeFravaersdag = forespoersel.bestemmendeFravaersdag()

            actualBestemmendeFravaersdag shouldBe it.expectedBestemmendeFravaersdag
        }

        test("ingen perioder kaster exception") {
            val forespoersel = mockTrengerInntekt().copy(
                egenmeldingsperioder = emptyList(),
                sykmeldingsperioder = emptyList(),
                bestemmendeFravaersdager = emptyMap()
            )

            shouldThrowExactly<UnsupportedOperationException> {
                forespoersel.bestemmendeFravaersdag()
            }
        }
    }
})

private data class TestData(
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val expectedBestemmendeFravaersdag: LocalDate?
)
