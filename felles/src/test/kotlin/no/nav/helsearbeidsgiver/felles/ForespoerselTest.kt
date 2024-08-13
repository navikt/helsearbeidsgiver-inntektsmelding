package no.nav.helsearbeidsgiver.felles

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.september

class ForespoerselTest :
    FunSpec({

        context(Forespoersel::forslagBestemmendeFravaersdag.name) {
            test("gir bestemmende fraværsdag for eget orgnr") {
                val orgnr = "555898023"

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 1.juli,
                                "444707112" to 13.mai,
                            ),
                    )

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 1.juli
            }

            test("beregner bestemmende fraværsdag dersom det mangler for eget orgnr") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = "555898023",
                        sykmeldingsperioder =
                            listOf(
                                5.januar til 30.januar,
                            ),
                        bestemmendeFravaersdager =
                            mapOf(
                                "444707112" to 13.mai,
                            ),
                    )

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 5.januar
            }
        }

        context(Forespoersel::forslagInntektsdato.name) {
            test("gir minste bestemmende fraværsdag (når det stammer fra eget orgnr)") {
                val orgnr = "333848343"

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 28.februar,
                                "851993994" to 1.mars,
                                "900505434" to 3.mars,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 28.februar
            }

            test("gir minste bestemmende fraværsdag (når det stammer fra annet orgnr)") {
                val orgnr = "333848343"

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 9.november,
                                "851993994" to 4.november,
                                "900505434" to 2.desember,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 4.november
            }

            test("gir minste bestemmende fraværsdag selv uten eget orgnr") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = "333848343",
                        bestemmendeFravaersdager =
                            mapOf(
                                "851993994" to 7.juni,
                                "900505434" to 5.juni,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 5.juni
            }

            test("beregner bestemmende fraværsdag dersom ingen er tilstede") {
                val forespoersel =
                    mockForespoersel().copy(
                        sykmeldingsperioder =
                            listOf(
                                2.februar til 28.februar,
                            ),
                        bestemmendeFravaersdager = emptyMap(),
                    )

                forespoersel.forslagInntektsdato() shouldBe 2.februar
            }
        }

        context(Forespoersel::eksternBestemmendeFravaersdag.name) {
            test("gir minste bestemmende fraværsdag blant andre orgnr (når eget orgnr tilstede)") {
                val orgnr = "666747222"

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 12.august,
                                "567923412" to 19.september,
                                "393540723" to 27.september,
                            ),
                    )

                forespoersel.eksternBestemmendeFravaersdag() shouldBe 19.september
            }

            test("gir minste bestemmende fraværsdag blant andre orgnr (når eget orgnr mangler)") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = "666747222",
                        bestemmendeFravaersdager =
                            mapOf(
                                "567923412" to 13.juli,
                                "393540723" to 12.juli,
                            ),
                    )

                forespoersel.eksternBestemmendeFravaersdag() shouldBe 12.juli
            }

            test("gir 'null' dersom bestemmende fraværsdag kun er tilstede for eget orgnr") {
                val orgnr = "666747222"

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 12.august,
                            ),
                    )

                forespoersel.eksternBestemmendeFravaersdag().shouldBeNull()
            }

            test("gir 'null' dersom ingen bestemmende fraværsdager er tilstede (heller ikke for eget orgnr)") {
                val forespoersel =
                    mockForespoersel().copy(
                        bestemmendeFravaersdager = emptyMap(),
                    )

                forespoersel.eksternBestemmendeFravaersdag().shouldBeNull()
            }
        }
    })
