package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockAvsenderSystem
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.felles.test.mock.randomDigitString
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.Kanal
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.inntektsmelding.joark.Mock.toMap
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.time.LocalDate
import java.util.UUID
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender as KlientAvsender

class JournalfoerImRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockDokArkivKlient = mockk<DokArkivClient>()

        mockConnectToRapid(testRapid) {
            listOf(
                JournalfoerImRiver(mockDokArkivKlient),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("oppretter journalpost og publiserer melding for å lagre journalpost-ID") {
            withData(
                mapOf(
                    "forespurt inntektsmelding" to Pair(EventName.INNTEKTSMELDING_MOTTATT, Mock.inntektsmelding),
                    "selvbestemt inntektsmelding" to Pair(EventName.SELVBESTEMT_IM_LAGRET, Mock.selvbestemtInntektsmelding),
                ),
            ) { (innkommendeEvent, inntektsmelding) ->
                val journalpostId = randomDigitString(6)
                val innkommendeMelding = Mock.innkommendeMelding(innkommendeEvent, inntektsmelding)

                coEvery {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mock.opprettOgFerdigstillResponse(journalpostId)

                testRapid.sendJson(innkommendeMelding.toMap())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
                        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                        Key.JOURNALPOST_ID to journalpostId.toJson(),
                        Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                    )

                coVerifySequence {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                        tittel = "Inntektsmelding",
                        gjelderPerson = GjelderPerson(inntektsmelding.sykmeldt.fnr.verdi),
                        avsender =
                            KlientAvsender.Organisasjon(
                                orgnr = inntektsmelding.avsender.orgnr.verdi,
                                navn = inntektsmelding.avsender.orgNavn,
                            ),
                        datoMottatt = LocalDate.now(),
                        dokumenter =
                            withArg {
                                it shouldHaveSize 1
                                it.first().dokumentVarianter.map(DokumentVariant::filtype) shouldContainExactly listOf("XML", "PDFA")
                            },
                        eksternReferanseId = "ARI-${innkommendeMelding.inntektsmelding.id}",
                        callId = "callId_${innkommendeMelding.inntektsmelding.id}",
                        kanal = Kanal.NAV_NO,
                    )
                }
            }
        }

        test("oppretter journalpost og publiserer melding for å lagre journalpost-ID ved inntektsmelding fra LPS-API") {
            val innkommendeEvent = EventName.INNTEKTSMELDING_MOTTATT
            val inntektsmelding = Mock.eksternInntektsmelding
            val journalpostId = randomDigitString(6)
            val innkommendeMelding = Mock.innkommendeMelding(innkommendeEvent, inntektsmelding)

            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mock.opprettOgFerdigstillResponse(journalpostId)

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
                    Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
                    Key.JOURNALPOST_ID to journalpostId.toJson(),
                    Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                )

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                    tittel = "Inntektsmelding",
                    gjelderPerson = GjelderPerson(inntektsmelding.sykmeldt.fnr.verdi),
                    avsender =
                        KlientAvsender.Organisasjon(
                            orgnr = inntektsmelding.avsender.orgnr.verdi,
                            navn = inntektsmelding.avsender.orgNavn,
                        ),
                    datoMottatt = LocalDate.now(),
                    dokumenter =
                        withArg {
                            it shouldHaveSize 1
                            it.first().dokumentVarianter.map(DokumentVariant::filtype) shouldContainExactly listOf("XML", "PDFA")
                        },
                    eksternReferanseId = "ARI-${innkommendeMelding.inntektsmelding.id}",
                    callId = "callId_${innkommendeMelding.inntektsmelding.id}",
                    kanal = Kanal.HR_SYSTEM_API,
                )
            }
        }

        test("håndterer klientfeil") {
            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("dette går itj', nei!")

            val innkommendeMelding = Mock.innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, Mock.inntektsmelding)

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke journalføre.",
                    kontekstId = innkommendeMelding.kontekstId,
                    utloesendeMelding = innkommendeJsonMap,
                )

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        context("ignorerer melding") {
            test("forespurt uten forespørsel") {
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, Mock.inntektsmelding)
                        .copy(forespoersel = null)
                        .toMap(),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
                }
            }

            withData(
                mapOf(
                    "melding med uønsket event" to Pair(Key.EVENT_NAME, EventName.SERVICE_HENT_TILGANG_ORG.toJson()),
                    "melding med behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, Mock.inntektsmelding)
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }

        context("bestemmende fraværsdag") {
            val bfFraForslag = 17.oktober
            val bfFraSykmeldinger = 19.oktober
            val bfFraAgp = 18.oktober
            val forespoersel =
                mockForespoersel().let {
                    it.copy(
                        bestemmendeFravaersdager =
                            mapOf(
                                it.orgnr to bfFraForslag,
                            ),
                    )
                }
            val inntektsmelding =
                Mock.inntektsmelding.copy(
                    sykmeldingsperioder =
                        listOf(
                            5.oktober til 15.oktober,
                            bfFraSykmeldinger til 3.november,
                        ),
                    agp =
                        Arbeidsgiverperiode(
                            perioder =
                                listOf(
                                    5.oktober til 15.oktober,
                                    bfFraAgp til 22.oktober,
                                ),
                            redusertLoennIAgp = null,
                        ),
                )

            test("forespurt med agp og forslag") {
                val innkommendeMelding =
                    Mock
                        .innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, inntektsmelding)
                        .copy(forespoersel = forespoersel)

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(innkommendeMelding.toMap())

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraAgp)
                    }
                }
            }

            test("forespurt med agp, men uten forslag") {
                val innkommendeMelding =
                    Mock
                        .innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, inntektsmelding)
                        .copy(
                            forespoersel = forespoersel.copy(bestemmendeFravaersdager = emptyMap()),
                        )

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(innkommendeMelding.toMap())

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraAgp)
                    }
                }
            }

            test("forespurt uten agp, men med forslag") {
                val innkommendeMelding =
                    Mock
                        .innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, inntektsmelding.copy(agp = null))
                        .copy(forespoersel = forespoersel)

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(innkommendeMelding.toMap())

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraForslag)
                    }
                }
            }

            test("forespurt uten agp og forslag") {
                val innkommendeMelding =
                    Mock
                        .innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, inntektsmelding.copy(agp = null))
                        .copy(
                            forespoersel = forespoersel.copy(bestemmendeFravaersdager = emptyMap()),
                        )

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(innkommendeMelding.toMap())

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraSykmeldinger)
                    }
                }
            }

            test("selvbestemt med agp") {
                val selvbestemt =
                    inntektsmelding.copy(
                        type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()),
                    )
                val innkommendeMelding = Mock.innkommendeMelding(EventName.SELVBESTEMT_IM_LAGRET, selvbestemt)

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(innkommendeMelding.toMap())

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraAgp)
                    }
                }
            }

            test("selvbestemt uten agp") {
                val selvbestemt =
                    inntektsmelding.copy(
                        type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()),
                        agp = null,
                    )
                val innkommendeMelding = Mock.innkommendeMelding(EventName.SELVBESTEMT_IM_LAGRET, selvbestemt)

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(innkommendeMelding.toMap())

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraSykmeldinger)
                    }
                }
            }

            test("selvbestemt uten agp, men med forslag fra forespørsel (ignorerer forespørsel)") {
                val selvbestemt =
                    inntektsmelding.copy(
                        type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()),
                        agp = null,
                    )
                val innkommendeMelding = Mock.innkommendeMelding(EventName.SELVBESTEMT_IM_LAGRET, selvbestemt)

                mockStatic(::tilDokumenter) {
                    testRapid.sendJson(
                        innkommendeMelding
                            .toMap()
                            .plusData(Key.FORESPOERSEL to forespoersel.toJson()),
                    )

                    verify(exactly = 1) {
                        tilDokumenter(innkommendeMelding.inntektsmelding, bfFraSykmeldinger)
                    }
                }
            }
        }
    })

private object Mock {
    val inntektsmelding = mockInntektsmeldingV1()
    val selvbestemtInntektsmelding = mockInntektsmeldingV1().copy(type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()))
    val eksternInntektsmelding =
        mockInntektsmeldingV1().copy(
            type =
                Inntektsmelding.Type.ForespurtEkstern(
                    id = UUID.randomUUID(),
                    _avsenderSystem = mockAvsenderSystem(),
                ),
        )

    val fail = mockFail("I don't think we're in Kansas anymore.", EventName.INNTEKTSMELDING_MOTTATT)

    fun innkommendeMelding(
        eventName: EventName,
        inntektsmelding: Inntektsmelding,
    ): JournalfoerImMelding =
        JournalfoerImMelding(
            eventName = eventName,
            kontekstId = UUID.randomUUID(),
            forespoersel = mockForespoersel(),
            inntektsmelding = inntektsmelding,
        )

    fun JournalfoerImMelding.toMap(): Map<Key, JsonElement> {
        val data =
            when (eventName) {
                EventName.INNTEKTSMELDING_MOTTATT -> {
                    mapOf(
                        Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                        Key.FORESPOERSEL to forespoersel?.toJson(),
                    ).mapValuesNotNull { it }
                }

                EventName.SELVBESTEMT_IM_LAGRET -> {
                    mapOf(
                        Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                    )
                }

                else -> {
                    throw IllegalStateException("Ugyldig verdi for 'eventName': '$eventName'")
                }
            }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to data.toJson(),
        )
    }

    fun opprettOgFerdigstillResponse(journalpostId: String): OpprettOgFerdigstillResponse =
        OpprettOgFerdigstillResponse(
            journalpostId = journalpostId,
            journalpostFerdigstilt = true,
            melding = null,
            dokumenter = emptyList(),
        )
}
