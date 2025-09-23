package no.nav.hag.simba.kontrakt.domene.forespoersel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.utenPaakrevdAGP
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.april
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
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class ForespoerselTest :
    FunSpec({

        context(Forespoersel::forslagBestemmendeFravaersdag.name) {
            test("bestemmende fraværsdag-forslaget fra Spleis overstyres av eldre sykmelding + egenmelding") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        sykmeldingsperioder = listOf(2.januar til 31.januar),
                        egenmeldingsperioder = listOf(1.januar til 1.januar),
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 1.juli,
                                Orgnr.genererGyldig() to 13.mai,
                            ),
                    )

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 1.januar
            }

            test("bestemmende fraværsdag-forslaget fra Spleis brukes dersom den er tidligere enn sykmelding + egenmelding") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        sykmeldingsperioder =
                            listOf(
                                1.august til 31.august,
                            ),
                        egenmeldingsperioder = listOf(30.juli til 30.juli),
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 1.juli,
                                Orgnr.genererGyldig() to 13.mai,
                            ),
                    )

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 1.juli
            }

            test("bestemmende fraværsdag-forslaget fra Spleis brukes dersom det ikke finnes egenmeldinger") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        sykmeldingsperioder =
                            listOf(
                                15.juni til 15.juli,
                            ),
                        egenmeldingsperioder = emptyList(),
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 1.juli,
                                Orgnr.genererGyldig() to 13.mai,
                            ),
                    )

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 1.juli
            }

            test("beregner bestemmende fraværsdag dersom det mangler for eget orgnr") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = Orgnr.genererGyldig(),
                        sykmeldingsperioder =
                            listOf(
                                5.januar til 30.januar,
                            ),
                        egenmeldingsperioder = listOf(1.januar til 1.januar),
                        bestemmendeFravaersdager =
                            mapOf(
                                Orgnr.genererGyldig() to 13.mai,
                            ),
                    )

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 5.januar
            }

            test("ignorer egenmeldinger ved beregning av bestemmende fraværsdag dersom agp ikke er påkrevd") {
                val forespoersel =
                    mockForespoersel()
                        .copy(
                            orgnr = Orgnr.genererGyldig(),
                            sykmeldingsperioder = listOf(7.mars til 31.mars),
                            egenmeldingsperioder = listOf(5.mars til 6.mars),
                            bestemmendeFravaersdager = mapOf(Orgnr.genererGyldig() to 14.april),
                        ).utenPaakrevdAGP()

                forespoersel.forslagBestemmendeFravaersdag() shouldBe 7.mars
            }
        }

        context(Forespoersel::forslagInntektsdato.name) {
            test("gir minste bestemmende fraværsdag (når det stammer fra eget orgnr og den er tidligere enn sykmelding + egenmelding)") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        sykmeldingsperioder =
                            listOf(
                                1.desember til 31.desember,
                            ),
                        egenmeldingsperioder = listOf(28.november til 29.november),
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 28.februar,
                                Orgnr.genererGyldig() to 1.mars,
                                Orgnr.genererGyldig() to 3.mars,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 28.februar
            }

            test("gir minste bestemmende fraværsdag (når det stammer fra annet orgnr og den er tidligere enn sykmelding + egenmelding)") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        sykmeldingsperioder =
                            listOf(
                                1.desember til 31.desember,
                            ),
                        egenmeldingsperioder = listOf(28.november til 29.november),
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 9.november,
                                Orgnr.genererGyldig() to 4.november,
                                Orgnr.genererGyldig() to 2.desember,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 4.november
            }

            test("gir minste bestemmende fraværsdag selv uten eget orgnr når den er tidligere enn sykmelding + egenmelding") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = Orgnr.genererGyldig(),
                        sykmeldingsperioder =
                            listOf(
                                1.desember til 31.desember,
                            ),
                        egenmeldingsperioder = listOf(28.november til 29.november),
                        bestemmendeFravaersdager =
                            mapOf(
                                Orgnr.genererGyldig() to 7.juni,
                                Orgnr.genererGyldig() to 5.juni,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 5.juni
            }

            test("gir minste bestemmende fraværsdag dersom det ikke finnes egenmeldinger") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        sykmeldingsperioder =
                            listOf(
                                1.desember til 31.desember,
                            ),
                        egenmeldingsperioder = emptyList(),
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 11.desember,
                                Orgnr.genererGyldig() to 8.desember,
                                Orgnr.genererGyldig() to 7.desember,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 7.desember
            }

            test("beregner bestemmende fraværsdag fra sykmelding + egenmelding dersom de kommer tidligere enn bestemmende fraværsdager fra Spleis") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = Orgnr.genererGyldig(),
                        sykmeldingsperioder =
                            listOf(
                                2.januar til 31.januar,
                            ),
                        egenmeldingsperioder = listOf(1.januar til 1.januar),
                        bestemmendeFravaersdager =
                            mapOf(
                                Orgnr.genererGyldig() to 7.juni,
                                Orgnr.genererGyldig() to 5.juni,
                            ),
                    )

                forespoersel.forslagInntektsdato() shouldBe 1.januar
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

            test("ignorer egenmeldinger ved beregning av bestemmende fraværsdag dersom agp ikke er påkrevd") {
                val forespoersel =
                    mockForespoersel()
                        .copy(
                            orgnr = Orgnr.genererGyldig(),
                            sykmeldingsperioder = listOf(8.august til 28.august),
                            egenmeldingsperioder = listOf(2.august til 7.august),
                            bestemmendeFravaersdager = emptyMap(),
                        ).utenPaakrevdAGP()

                forespoersel.forslagInntektsdato() shouldBe 8.august
            }
        }

        context(Forespoersel::eksternInntektsdato.name) {
            test("gir minste bestemmende fraværsdag blant andre orgnr (når eget orgnr tilstede)") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 12.august,
                                Orgnr.genererGyldig() to 19.september,
                                Orgnr.genererGyldig() to 27.september,
                            ),
                    )

                forespoersel.eksternInntektsdato() shouldBe 19.september
            }

            test("gir minste bestemmende fraværsdag blant andre orgnr (når eget orgnr mangler)") {
                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = Orgnr.genererGyldig(),
                        bestemmendeFravaersdager =
                            mapOf(
                                Orgnr.genererGyldig() to 13.juli,
                                Orgnr.genererGyldig() to 12.juli,
                            ),
                    )

                forespoersel.eksternInntektsdato() shouldBe 12.juli
            }

            test("gir 'null' dersom bestemmende fraværsdag kun er tilstede for eget orgnr") {
                val orgnr = Orgnr.genererGyldig()

                val forespoersel =
                    mockForespoersel().copy(
                        orgnr = orgnr,
                        bestemmendeFravaersdager =
                            mapOf(
                                orgnr to 12.august,
                            ),
                    )

                forespoersel.eksternInntektsdato().shouldBeNull()
            }

            test("gir 'null' dersom ingen bestemmende fraværsdager er tilstede (heller ikke for eget orgnr)") {
                val forespoersel =
                    mockForespoersel().copy(
                        bestemmendeFravaersdager = emptyMap(),
                    )

                forespoersel.eksternInntektsdato().shouldBeNull()
            }
        }
    })
