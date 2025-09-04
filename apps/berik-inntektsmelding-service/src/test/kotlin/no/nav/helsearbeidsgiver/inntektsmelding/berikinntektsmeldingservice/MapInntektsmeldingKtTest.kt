package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.hag.simba.utils.felles.test.mock.mockForespoersel
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.hag.simba.utils.felles.test.mock.utenPaakrevdAGP
import no.nav.hag.simba.utils.felles.test.mock.utenPaakrevdInntekt
import no.nav.hag.simba.utils.felles.test.mock.utenPaakrevdRefusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.date.toOffsetDateTimeOslo
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.kl
import java.util.UUID

class MapInntektsmeldingKtTest :
    FunSpec({

        context(::mapInntektsmelding.name) {

            test("inntektsmelding mappes korrekt med AGP, inntekt og refusjon påkrevd") {
                val inntektsmeldingId = UUID.randomUUID()
                val forespoersel = mockForespoersel()
                val skjema = mockSkjemaInntektsmelding()
                val virksomhetNavn = "Skrekkinngytende smaker LLC"
                val sykmeldtNavn = "Runar fra Regnskap"
                val avsenderNavn = "Hege fra HR"
                val imMottatt = 11.juli.kl(10, 10, 0, 0)

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = inntektsmeldingId,
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = virksomhetNavn,
                        sykmeldtNavn = sykmeldtNavn,
                        avsenderNavn = avsenderNavn,
                        mottatt = imMottatt,
                        innsending = null, // TODO: test med innsending != null (API-innsendt) også.
                    )

                inntektsmelding.apply {
                    id shouldBe inntektsmeldingId

                    type shouldBe
                        Inntektsmelding.Type.Forespurt(
                            id = skjema.forespoerselId,
                        )

                    sykmeldt shouldBe
                        Sykmeldt(
                            fnr = forespoersel.fnr,
                            navn = sykmeldtNavn,
                        )

                    avsender shouldBe
                        Avsender(
                            orgnr = forespoersel.orgnr,
                            orgNavn = virksomhetNavn,
                            navn = avsenderNavn,
                            tlf = skjema.avsenderTlf,
                        )

                    sykmeldingsperioder.shouldNotBeEmpty()
                    sykmeldingsperioder shouldBe forespoersel.sykmeldingsperioder

                    agp.shouldNotBeNull()
                    agp shouldBe skjema.agp

                    inntekt.shouldNotBeNull()
                    inntekt shouldBe skjema.inntekt

                    refusjon.shouldNotBeNull()
                    refusjon shouldBe skjema.refusjon

                    aarsakInnsending shouldBe AarsakInnsending.Endring

                    vedtaksperiodeId shouldBe forespoersel.vedtaksperiodeId

                    mottatt shouldBe imMottatt.toOffsetDateTimeOslo()
                }
            }

            test("beholder tomme verdier for AGP, inntekt og refusjon") {
                val forespoersel = mockForespoersel()
                val skjema =
                    mockSkjemaInntektsmelding().copy(
                        agp = null,
                        inntekt = null,
                        refusjon = null,
                    )

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                inntektsmelding.agp.shouldBeNull()
                inntektsmelding.inntekt.shouldBeNull()
                inntektsmelding.refusjon.shouldBeNull()
            }

            test("fjerner AGP dersom AGP _ikke_ er påkrevd") {
                val forespoersel = mockForespoersel().utenPaakrevdAGP()
                val skjema = mockSkjemaInntektsmelding()

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                skjema.agp.shouldNotBeNull()
                inntektsmelding.agp.shouldBeNull()
            }

            test("fjerner inntekt dersom inntekt _ikke_ er påkrevd") {
                val forespoersel = mockForespoersel().utenPaakrevdInntekt()
                val skjema = mockSkjemaInntektsmelding()

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                skjema.inntekt.shouldNotBeNull()
                inntektsmelding.inntekt.shouldBeNull()
            }

            test("bruker innsendt inntektsdato dersom AGP er påkrevd") {
                val forespoersel = mockForespoersel()
                val skjema =
                    mockSkjemaInntektsmelding().let {
                        it.copy(
                            inntekt =
                                it.inntekt?.copy(
                                    inntektsdato = 8.desember,
                                ),
                        )
                    }

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                inntektsmelding.inntekt.also {
                    it.shouldNotBeNull()
                    it.inntektsdato shouldBe skjema.inntekt?.inntektsdato
                    it.inntektsdato shouldNotBe forespoersel.forslagInntektsdato()
                }
            }

            test("overser innsendt inntektsdato og bruker forslag (fra Spleis) dersom AGP _ikke_ er påkrevd") {
                val forespoersel = mockForespoersel().utenPaakrevdAGP()
                val skjema =
                    mockSkjemaInntektsmelding().let {
                        it.copy(
                            inntekt =
                                it.inntekt?.copy(
                                    inntektsdato = 14.desember,
                                ),
                        )
                    }

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                inntektsmelding.inntekt.also {
                    it.shouldNotBeNull()
                    it.inntektsdato shouldNotBe skjema.inntekt?.inntektsdato
                    it.inntektsdato shouldBe forespoersel.forslagInntektsdato()
                }
            }

            test("bruker beregnet bestemmende fraværsdag som inntektsdato dersom forslag (fra Spleis) til inntektsdato mangler og AGP _ikke_ er påkrevd") {
                val forespoersel =
                    mockForespoersel().utenPaakrevdAGP().copy(
                        sykmeldingsperioder =
                            listOf(
                                4.juli til 28.juli,
                            ),
                        bestemmendeFravaersdager = emptyMap(),
                    )
                val skjema =
                    mockSkjemaInntektsmelding().let {
                        it.copy(
                            inntekt =
                                it.inntekt?.copy(
                                    inntektsdato = 3.august,
                                ),
                        )
                    }

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                inntektsmelding.inntekt.also {
                    it.shouldNotBeNull()
                    it.inntektsdato shouldNotBe skjema.inntekt?.inntektsdato
                    it.inntektsdato shouldBe forespoersel.forslagInntektsdato()
                    it.inntektsdato shouldBe 4.juli
                }
            }

            test("bruker tom refusjon dersom refusjon _ikke_ er påkrevd") {
                val forespoersel = mockForespoersel().utenPaakrevdRefusjon()
                val skjema = mockSkjemaInntektsmelding()

                val inntektsmelding =
                    mapInntektsmelding(
                        inntektsmeldingId = UUID.randomUUID(),
                        forespoersel = forespoersel,
                        skjema = skjema,
                        aarsakInnsending = AarsakInnsending.Endring,
                        virksomhetNavn = "Skrekkinngytende smaker LLC",
                        sykmeldtNavn = "Runar fra Regnskap",
                        avsenderNavn = "Hege fra HR",
                        mottatt = 6.desember.atStartOfDay(),
                    )

                skjema.refusjon.shouldNotBeNull()
                inntektsmelding.refusjon.shouldBeNull()
            }
        }
    })
