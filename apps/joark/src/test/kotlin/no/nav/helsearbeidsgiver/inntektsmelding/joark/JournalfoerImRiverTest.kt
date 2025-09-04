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
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.model.Fail
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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.inntektsmelding.joark.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
                    "forespurt inntektsmelding" to Triple(EventName.INNTEKTSMELDING_MOTTATT, Key.INNTEKTSMELDING, Mock.inntektsmelding),
                    "selvbestemt inntektsmelding" to Triple(EventName.SELVBESTEMT_IM_LAGRET, Key.SELVBESTEMT_INNTEKTSMELDING, Mock.selvbestemtInntektsmelding),
                ),
            ) { (innkommendeEvent, inntektsmeldingKey, inntektsmelding) ->
                val journalpostId = randomDigitString(6)
                val innkommendeMelding = Mock.innkommendeMelding(innkommendeEvent, inntektsmelding)

                coEvery {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mock.opprettOgFerdigstillResponse(journalpostId)

                testRapid.sendJson(innkommendeMelding.toMap(inntektsmeldingKey))

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
            val inntektsmeldingKey = Key.INNTEKTSMELDING
            val journalpostId = randomDigitString(6)
            val innkommendeMelding = Mock.innkommendeMelding(innkommendeEvent, inntektsmelding)

            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mock.opprettOgFerdigstillResponse(journalpostId)

            testRapid.sendJson(innkommendeMelding.toMap(inntektsmeldingKey))

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
            withData(
                mapOf(
                    "melding med uønsket event" to Pair(Key.EVENT_NAME, EventName.TILGANG_ORG_REQUESTED.toJson()),
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
    })

private object Mock {
    val inntektsmelding = mockInntektsmeldingV1()
    val selvbestemtInntektsmelding = mockInntektsmeldingV1().copy(type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()))
    val eksternInntektsmelding =
        mockInntektsmeldingV1().copy(
            type = Inntektsmelding.Type.ForespurtEkstern(UUID.randomUUID(), AvsenderSystem(Orgnr.genererGyldig(), "Slapp Tiger")),
        )

    val fail = mockFail("I don't think we're in Kansas anymore.", EventName.INNTEKTSMELDING_MOTTATT)

    fun innkommendeMelding(
        eventName: EventName,
        inntektsmelding: Inntektsmelding,
    ): JournalfoerImMelding =
        JournalfoerImMelding(
            eventName = eventName,
            kontekstId = UUID.randomUUID(),
            inntektsmelding = inntektsmelding,
        )

    fun JournalfoerImMelding.toMap(imKey: Key = Key.INNTEKTSMELDING): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    imKey to inntektsmelding.toJson(Inntektsmelding.serializer()),
                ).toJson(),
        )

    fun opprettOgFerdigstillResponse(journalpostId: String): OpprettOgFerdigstillResponse =
        OpprettOgFerdigstillResponse(
            journalpostId = journalpostId,
            journalpostFerdigstilt = true,
            melding = null,
            dokumenter = emptyList(),
        )
}
