package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.test.mock.tilForespoersel
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class MapInntektsmeldingKtTest :
    FunSpec({

        context(::mapInntektsmelding.name) {

            test("inntektsmelding mappes korrekt") {
                val forespoersel = Mock.forespoersel()
                val skjema = Mock.skjema()
                val sykmeldtNavn = "Runar fra Regnskap"
                val innsenderNavn = "Hege fra HR"
                val virksomhetNavn = "Skrekkinngytende smaker LLC"

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = sykmeldtNavn,
                        virksomhetNavn = virksomhetNavn,
                        innsenderNavn = innsenderNavn,
                    )

                inntektsmelding.apply {
                    // Matcher forespørsel
                    vedtaksperiodeId shouldBe forespoersel.vedtaksperiodeId
                    orgnrUnderenhet shouldBe forespoersel.orgnr
                    identitetsnummer shouldBe forespoersel.fnr
                    fraværsperioder shouldBe forespoersel.sykmeldingsperioder
                    forespurtData shouldBe
                        listOf(
                            "arbeidsgiverperiode",
                            "inntekt",
                            "refusjon",
                        )

                    // Matcher skjema
                    egenmeldingsperioder shouldBe skjema.egenmeldingsperioder
                    arbeidsgiverperioder shouldBe skjema.arbeidsgiverperioder
                    inntekt shouldBe skjema.inntekt
                    beregnetInntekt shouldBe skjema.inntekt.beregnetInntekt
                    inntektsdato shouldBe skjema.bestemmendeFraværsdag
                    fullLønnIArbeidsgiverPerioden shouldBe skjema.fullLønnIArbeidsgiverPerioden
                    refusjon shouldBe skjema.refusjon
                    naturalytelser shouldBe skjema.naturalytelser
                    årsakInnsending shouldBe skjema.årsakInnsending
                    telefonnummer shouldBe skjema.telefonnummer

                    // Matcher andre argumenter
                    fulltNavn shouldBe sykmeldtNavn
                    virksomhetNavn shouldBe virksomhetNavn
                    innsenderNavn shouldBe innsenderNavn

                    // Matcher faste verdier
                    behandlingsdager.shouldBeEmpty()
                    tidspunkt.shouldBeWithin(5.seconds.toJavaDuration(), ZonedDateTime.now().toOffsetDateTime())

                    // Mathcer utregnet verdi
                    bestemmendeFraværsdag shouldBe 6.april
                }
            }

            test("erstatter egenmeldingsperioder fra skjema med forespoersel dersom AGP _ikke_ er påkrevd") {
                val forespoersel = Mock.forespoersel().utenPaakrevdAGP()
                val skjema =
                    Mock.skjema().copy(
                        egenmeldingsperioder =
                            listOf(
                                23.mars til 23.mars,
                                26.mars til 28.mars,
                            ),
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.egenmeldingsperioder shouldNotBe skjema.egenmeldingsperioder
                inntektsmelding.egenmeldingsperioder shouldBe forespoersel.egenmeldingsperioder
            }

            test("fjerner AGP dersom AGP _ikke_ er påkrevd") {
                val forespoersel = Mock.forespoersel().utenPaakrevdAGP()
                val skjema = Mock.skjema()

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                skjema.arbeidsgiverperioder.shouldNotBeEmpty()
                inntektsmelding.arbeidsgiverperioder.shouldBeEmpty()
            }

            test("fjerner 'fullLoennIArbeidsgiverPerioden' dersom AGP _ikke_ er påkrevd") {
                val forespoersel = Mock.forespoersel().utenPaakrevdAGP()
                val skjema = Mock.skjema()

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                skjema.fullLønnIArbeidsgiverPerioden.shouldNotBeNull()
                inntektsmelding.fullLønnIArbeidsgiverPerioden.shouldBeNull()
            }

            test("fjerner ugyldige verdier fra 'fullLoennIArbeidsgiverPerioden' dersom AG utbetaler full lønn i AGP") {
                val forespoersel = Mock.forespoersel()
                val skjema =
                    Mock.skjema().let {
                        it.copy(
                            fullLønnIArbeidsgiverPerioden =
                                it.fullLønnIArbeidsgiverPerioden?.copy(
                                    utbetalerFullLønn = true,
                                ),
                        )
                    }

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                skjema.fullLønnIArbeidsgiverPerioden shouldBe
                    FullLoennIArbeidsgiverPerioden(
                        utbetalerFullLønn = true,
                        begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.FravaerUtenGyldigGrunn,
                        utbetalt = 4444.0,
                    )

                inntektsmelding.fullLønnIArbeidsgiverPerioden shouldBe
                    FullLoennIArbeidsgiverPerioden(
                        utbetalerFullLønn = true,
                        begrunnelse = null,
                        utbetalt = null,
                    )
            }

            test("bruker innsendt inntektsdato dersom AGP er påkrevd") {
                val forespoersel = Mock.forespoersel()
                val skjema =
                    Mock.skjema().copy(
                        bestemmendeFraværsdag = 8.desember,
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.inntektsdato shouldBe skjema.bestemmendeFraværsdag
                inntektsmelding.inntektsdato shouldNotBe forespoersel.forslagInntektsdato()
            }

            test("overser innsendt inntektsdato og bruker forslag (fra Spleis) dersom AGP _ikke_ er påkrevd") {
                val forespoersel = Mock.forespoersel().utenPaakrevdAGP()
                val skjema =
                    Mock.skjema().copy(
                        bestemmendeFraværsdag = 14.desember,
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.inntektsdato shouldNotBe skjema.bestemmendeFraværsdag
                inntektsmelding.inntektsdato shouldBe forespoersel.forslagInntektsdato()
            }

            test("bruker beregnet bestemmende fraværsdag som inntektsdato dersom forslag (fra Spleis) til inntektsdato mangler og AGP _ikke_ er påkrevd") {
                val forespoersel =
                    Mock.forespoersel().utenPaakrevdAGP().copy(
                        sykmeldingsperioder =
                            listOf(
                                4.juli til 28.juli,
                            ),
                        bestemmendeFravaersdager = emptyMap(),
                    )
                val skjema =
                    Mock.skjema().copy(
                        bestemmendeFraværsdag = 3.august,
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.inntektsdato shouldNotBe skjema.bestemmendeFraværsdag
                inntektsmelding.inntektsdato shouldBe forespoersel.forslagInntektsdato()
                inntektsmelding.inntektsdato shouldBe 4.juli
            }

            test("bruker beregnet bestemmende fraværsdag dersom AGP er påkrevd") {
                val forespoersel =
                    Mock.forespoersel().let {
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
                val skjema =
                    Mock.skjema().copy(
                        arbeidsgiverperioder =
                            listOf(
                                5.mai til 9.mai,
                                12.mai til 22.mai,
                            ),
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.bestemmendeFraværsdag shouldBe 12.mai
                inntektsmelding.bestemmendeFraværsdag shouldNotBe forespoersel.forslagBestemmendeFravaersdag()
            }

            test("bruker beregnet bestemmende fraværsdag dersom kun refusjon er påkrevd") {
                val forespoersel =
                    Mock
                        .forespoersel()
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

                val skjema =
                    Mock.skjema().copy(
                        arbeidsgiverperioder =
                            listOf(
                                5.januar til 10.januar,
                                14.januar til 23.januar,
                            ),
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.bestemmendeFraværsdag shouldBe 14.januar
                inntektsmelding.bestemmendeFraværsdag shouldNotBe forespoersel.forslagBestemmendeFravaersdag()
            }

            test("bruker forslag (fra Spleis) som bestemmende fraværsdag dersom AGP _ikke_ er påkrevd") {
                val forespoersel =
                    Mock.forespoersel().utenPaakrevdAGP().let {
                        it.copy(
                            bestemmendeFravaersdager =
                                mapOf(
                                    it.orgnr to 8.mai,
                                ),
                        )
                    }
                val skjema =
                    Mock.skjema().copy(
                        fraværsperioder =
                            listOf(
                                15.mai til 17.juni,
                            ),
                        arbeidsgiverperioder = emptyList(),
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.bestemmendeFraværsdag shouldNotBe 15.mai
                inntektsmelding.bestemmendeFraværsdag shouldBe forespoersel.forslagBestemmendeFravaersdag()
            }

            test("bruker beregnet bestemmende fraværsdag dersom forslag (fra Spleis) til bestemmende fraværsdag mangler og AGP _ikke_ er påkrevd") {
                val forespoersel =
                    Mock.forespoersel().utenPaakrevdAGP().copy(
                        sykmeldingsperioder =
                            listOf(
                                10.august til 31.august,
                            ),
                        bestemmendeFravaersdager = emptyMap(),
                    )
                val skjema =
                    Mock.skjema().copy(
                        fraværsperioder =
                            listOf(
                                7.september til 25.september,
                            ),
                        arbeidsgiverperioder = emptyList(),
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.bestemmendeFraværsdag shouldNotBe 7.september
                inntektsmelding.bestemmendeFraværsdag shouldBe forespoersel.forslagBestemmendeFravaersdag()
                inntektsmelding.bestemmendeFraværsdag shouldBe 10.august
            }

            test("bruker innsendt inntekt dersom inntekt er påkrevd") {
                val forespoersel = Mock.forespoersel()
                val skjema = Mock.skjema()

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.inntekt shouldBe skjema.inntekt
                inntektsmelding.beregnetInntekt shouldBe skjema.inntekt.beregnetInntekt
            }

            test("bruker fastsatt inntekt (fra Spleis) dersom inntekt _ikke_ er påkrevd") {
                val forespoersel = Mock.forespoersel().utenPaakrevdInntekt()
                val skjema = Mock.skjema()

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                val fastsattInntekt = 8795.0

                inntektsmelding.inntekt shouldNotBe skjema.inntekt
                inntektsmelding.beregnetInntekt shouldNotBe skjema.inntekt.beregnetInntekt

                inntektsmelding.inntekt shouldBe
                    Inntekt(
                        bekreftet = true,
                        beregnetInntekt = fastsattInntekt,
                        endringÅrsak = null,
                        manueltKorrigert = true,
                    )
                inntektsmelding.beregnetInntekt shouldBe fastsattInntekt
            }

            test("bruker innsendt refusjon dersom refusjon er påkrevd og AG utbetaler hele eller deler av sykepengene") {
                val forespoersel = Mock.forespoersel()
                val skjema = Mock.skjema()

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.refusjon shouldBe skjema.refusjon
            }

            test("bruker tom refusjon dersom refusjon _ikke_ er påkrevd, selv om skjema påstår at AG utbetaler hele eller deler av sykepengene") {
                val forespoersel = Mock.forespoersel().utenPaakrevdRefusjon()
                val skjema = Mock.skjema()

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.refusjon shouldNotBe skjema.refusjon
                inntektsmelding.refusjon shouldBe
                    Refusjon(
                        utbetalerHeleEllerDeler = false,
                        refusjonPrMnd = null,
                        refusjonOpphører = null,
                        refusjonEndringer = null,
                    )
            }

            test("bruker tom refusjon dersom refusjon er påkrevd, men AG _ikke_ utbetaler hele eller deler av sykepengene") {
                val forespoersel = Mock.forespoersel()
                val skjema =
                    Mock.skjema().let {
                        it.copy(
                            refusjon =
                                it.refusjon.copy(
                                    utbetalerHeleEllerDeler = false,
                                ),
                        )
                    }

                val inntektsmelding =
                    mapInntektsmelding(
                        forespoersel = forespoersel,
                        skjema = skjema,
                        fulltnavnArbeidstaker = "Runar fra Regnskap",
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        innsenderNavn = "Hege fra HR",
                    )

                inntektsmelding.refusjon shouldNotBe skjema.refusjon
                inntektsmelding.refusjon shouldBe
                    Refusjon(
                        utbetalerHeleEllerDeler = false,
                        refusjonPrMnd = null,
                        refusjonOpphører = null,
                        refusjonEndringer = null,
                    )
            }
        }
    })

private object Mock {
    fun skjema(): Innsending =
        Innsending(
            orgnrUnderenhet = "671589013",
            identitetsnummer = "13117800123",
            behandlingsdager = emptyList(),
            egenmeldingsperioder =
                listOf(
                    2.april til 4.april,
                ),
            fraværsperioder =
                listOf(
                    6.april til 12.april,
                    13.april til 28.april,
                ),
            arbeidsgiverperioder =
                listOf(
                    2.april til 4.april,
                    6.april til 18.april,
                ),
            bestemmendeFraværsdag = 6.april,
            inntekt =
                Inntekt(
                    bekreftet = true,
                    beregnetInntekt = 32100.0,
                    endringÅrsak =
                        Ferie(
                            liste = listOf(10.mars til 20.mars),
                        ),
                    manueltKorrigert = true,
                ),
            fullLønnIArbeidsgiverPerioden =
                FullLoennIArbeidsgiverPerioden(
                    utbetalerFullLønn = false,
                    begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.FravaerUtenGyldigGrunn,
                    utbetalt = 4444.0,
                ),
            refusjon =
                Refusjon(
                    utbetalerHeleEllerDeler = true,
                    refusjonPrMnd = 333.0,
                    refusjonOpphører = 21.april,
                ),
            naturalytelser =
                listOf(
                    Naturalytelse(
                        naturalytelse = NaturalytelseKode.KOSTDOEGN,
                        dato = 23.april,
                        beløp = 222.0,
                    ),
                ),
            årsakInnsending = AarsakInnsending.ENDRING,
            bekreftOpplysninger = true,
        )

    fun forespoersel(): Forespoersel = skjema().tilForespoersel(UUID.randomUUID())
}

private fun Forespoersel.utenPaakrevdAGP(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                arbeidsgiverperiode =
                    ForespurtData.Arbeidsgiverperiode(
                        paakrevd = false,
                    ),
            ),
    )

private fun Forespoersel.utenPaakrevdInntekt(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                inntekt =
                    ForespurtData.Inntekt(
                        paakrevd = false,
                        forslag =
                            ForslagInntekt.Fastsatt(
                                fastsattInntekt = 8795.0,
                            ),
                    ),
            ),
    )

private fun Forespoersel.utenPaakrevdRefusjon(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                refusjon =
                    ForespurtData.Refusjon(
                        paakrevd = false,
                        forslag =
                            ForslagRefusjon(
                                perioder = emptyList(),
                                opphoersdato = null,
                            ),
                    ),
            ),
    )
