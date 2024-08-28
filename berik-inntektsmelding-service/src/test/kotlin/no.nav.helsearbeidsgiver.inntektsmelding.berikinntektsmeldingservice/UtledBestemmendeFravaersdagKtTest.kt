package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai

class UtledBestemmendeFravaersdagKtTest :
    FunSpec({

        context(::utledBestemmendeFravaersdag.name) {

            test("bruker beregnet bestemmende fraværsdag dersom AGP er påkrevd") {
                val forespoersel =
                    mockForespoersel().let {
                        it.copy(
                            sykmeldingsperioder =
                                listOf(
                                    6.mai til 9.mai,
                                    12.mai til 27.mai,
                                ),
                            bestemmendeFravaersdager =
                                mapOf(
                                    it.orgnr to 1.mai,
                                ),
                        )
                    }
                val inntektsmelding =
                    mockInntektsmeldingV1().let {
                        it.copy(
                            agp =
                                it.agp?.copy(
                                    perioder =
                                        listOf(
                                            5.mai til 9.mai,
                                            12.mai til 22.mai,
                                        ),
                                ),
                        )
                    }

                val bestemmendeFravaersdag = utledBestemmendeFravaersdag(forespoersel, inntektsmelding)

                bestemmendeFravaersdag shouldBe 12.mai
                bestemmendeFravaersdag shouldNotBe forespoersel.forslagBestemmendeFravaersdag()
            }

            test("bruker beregnet bestemmende fraværsdag dersom kun refusjon er påkrevd") {
                val forespoersel =
                    mockForespoersel()
                        .utenPaakrevdAGP()
                        .utenPaakrevdInntekt()
                        .let {
                            it.copy(
                                sykmeldingsperioder =
                                    listOf(
                                        5.januar til 10.januar,
                                        14.januar til 28.januar,
                                    ),
                                bestemmendeFravaersdager =
                                    mapOf(
                                        it.orgnr to 3.januar,
                                    ),
                            )
                        }

                val inntektsmelding =
                    mockInntektsmeldingV1().let {
                        it.copy(
                            agp =
                                it.agp?.copy(
                                    perioder =
                                        listOf(
                                            5.januar til 10.januar,
                                            14.januar til 23.januar,
                                        ),
                                ),
                        )
                    }

                val bestemmendeFravaersdag = utledBestemmendeFravaersdag(forespoersel, inntektsmelding)

                bestemmendeFravaersdag shouldBe 14.januar
                bestemmendeFravaersdag shouldNotBe forespoersel.forslagBestemmendeFravaersdag()
            }

            test("bruker forslag (fra Spleis) som bestemmende fraværsdag dersom AGP _ikke_ er påkrevd og ikke kun refusjon påkrevd") {
                val forespoersel =
                    mockForespoersel().utenPaakrevdAGP().let {
                        it.copy(
                            sykmeldingsperioder =
                                listOf(
                                    15.mai til 17.juni,
                                ),
                            bestemmendeFravaersdager =
                                mapOf(
                                    it.orgnr to 8.mai,
                                ),
                        )
                    }
                val inntektsmelding =
                    mockInntektsmeldingV1().let {
                        it.copy(
                            agp =
                                it.agp?.copy(
                                    perioder = emptyList(),
                                ),
                        )
                    }

                val bestemmendeFravaersdag = utledBestemmendeFravaersdag(forespoersel, inntektsmelding)

                bestemmendeFravaersdag shouldNotBe 15.mai
                bestemmendeFravaersdag shouldBe forespoersel.forslagBestemmendeFravaersdag()
            }

            test("bruker beregnet bestemmende fraværsdag dersom forslag (fra Spleis) mangler og AGP _ikke_ er påkrevd og ikke kun refusjon påkrevd") {
                val forespoersel =
                    mockForespoersel().utenPaakrevdAGP().copy(
                        sykmeldingsperioder =
                            listOf(
                                10.august til 31.august,
                            ),
                        bestemmendeFravaersdager = emptyMap(),
                    )
                val inntektsmelding =
                    mockInntektsmeldingV1().let {
                        it.copy(
                            agp =
                                it.agp?.copy(
                                    perioder =
                                        listOf(
                                            5.august til 20.august,
                                        ),
                                ),
                        )
                    }

                val bestemmendeFravaersdag = utledBestemmendeFravaersdag(forespoersel, inntektsmelding)

                bestemmendeFravaersdag shouldNotBe 5.august
                bestemmendeFravaersdag shouldBe forespoersel.forslagBestemmendeFravaersdag()
                bestemmendeFravaersdag shouldBe 10.august
            }
        }
    })
