package no.nav.helsearbeidsgiver.inntektsmelding.joark

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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.joark.Mock.toMap
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import java.time.LocalDate
import java.util.UUID
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender as KlientAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1

class JournalfoerImRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockDokArkivKlient = mockk<DokArkivClient>()

        JournalfoerImRiver(mockDokArkivKlient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        context("oppretter journalpost og publiserer melding for å lagre journalpost-ID") {
            test("forespurt inntektsmelding") {
                val journalpostId = UUID.randomUUID().toString()
                val forespoerselId = UUID.randomUUID()
                val innsendingId = 1L

                val innkommendeMelding = Mock.innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, Mock.inntektsmelding, Mock.bestemmendeFravaersdag)

                coEvery {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
                } returns Mock.opprettOgFerdigstillResponse(journalpostId)

                testRapid.sendJson(
                    innkommendeMelding
                        .toMap()
                        .plus(
                            mapOf(
                                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                            ),
                        ),
                )

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.JOURNALPOST_ID to journalpostId.toJson(),
                        Key.INNTEKTSMELDING to Mock.inntektsmelding.toJson(InntektsmeldingV1.serializer()),
                        Key.BESTEMMENDE_FRAVAERSDAG to Mock.bestemmendeFravaersdag.toJson(),
                        Key.INNTEKTSMELDING_DOKUMENT to
                            innkommendeMelding.inntektsmelding.let {
                                runCatching { it.tilGammelInntektsmeldingJson() }
                                    .getOrDefault(it)
                            },
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                    )

                coVerifySequence {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                        tittel = "Inntektsmelding",
                        gjelderPerson = GjelderPerson(Mock.inntektsmelding.sykmeldt.fnr.verdi),
                        avsender =
                            KlientAvsender.Organisasjon(
                                orgnr = Mock.inntektsmelding.avsender.orgnr.verdi,
                                navn = Mock.inntektsmelding.avsender.orgNavn,
                            ),
                        datoMottatt = LocalDate.now(),
                        dokumenter =
                            withArg {
                                it shouldHaveSize 1
                                it.first().dokumentVarianter.map(DokumentVariant::filtype) shouldContainExactly listOf("XML", "PDFA")
                            },
                        eksternReferanseId = "ARI-${innkommendeMelding.transaksjonId}",
                        callId = "callId_${innkommendeMelding.transaksjonId}",
                    )
                }
            }

            test("selvbestemt inntektsmelding") {
                val journalpostId = UUID.randomUUID().toString()
                val selvbestemtId = UUID.randomUUID()

                val innkommendeMelding = Mock.innkommendeMelding(EventName.SELVBESTEMT_IM_LAGRET, Mock.inntektsmelding)

                coEvery {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
                } returns Mock.opprettOgFerdigstillResponse(journalpostId)

                testRapid.sendJson(
                    innkommendeMelding
                        .toMap(Key.SELVBESTEMT_INNTEKTSMELDING)
                        .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson()),
                )

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.firstMessage().toMap() shouldContainExactly
                    mapOf(
                        Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                        Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(),
                        Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                        Key.JOURNALPOST_ID to journalpostId.toJson(),
                        Key.INNTEKTSMELDING to Mock.inntektsmelding.toJson(InntektsmeldingV1.serializer()),
                        Key.INNTEKTSMELDING_DOKUMENT to
                            innkommendeMelding.inntektsmelding.let {
                                runCatching { it.tilGammelInntektsmeldingJson() }
                                    .getOrDefault(it)
                            },
                        Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                    )

                coVerifySequence {
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                        tittel = "Inntektsmelding",
                        gjelderPerson = GjelderPerson(Mock.inntektsmelding.sykmeldt.fnr.verdi),
                        avsender =
                            KlientAvsender.Organisasjon(
                                orgnr = Mock.inntektsmelding.avsender.orgnr.verdi,
                                navn = Mock.inntektsmelding.avsender.orgNavn,
                            ),
                        datoMottatt = LocalDate.now(),
                        dokumenter =
                            withArg {
                                it shouldHaveSize 1
                                it.first().dokumentVarianter.map(DokumentVariant::filtype) shouldContainExactly listOf("XML", "PDFA")
                            },
                        eksternReferanseId = "ARI-${innkommendeMelding.transaksjonId}",
                        callId = "callId_${innkommendeMelding.transaksjonId}",
                    )
                }
            }
        }

        test("håndterer retries (med BehovType.JOURNALFOER)") {
            val journalpostId = UUID.randomUUID().toString()
            val selvbestemtId = UUID.randomUUID()

            val innkommendeMelding =
                Mock.innkommendeMelding(
                    EventName.SELVBESTEMT_IM_LAGRET,
                    Mock.inntektsmelding,
                )

            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            } returns Mock.opprettOgFerdigstillResponse(journalpostId)

            testRapid.sendJson(
                innkommendeMelding
                    .toMap(Key.SELVBESTEMT_INNTEKTSMELDING)
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson())
                    .plus(Key.BEHOV to BehovType.JOURNALFOER.toJson()),
            )

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.BEHOV to BehovType.LAGRE_JOURNALPOST_ID.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.JOURNALPOST_ID to journalpostId.toJson(),
                    Key.INNTEKTSMELDING to Mock.inntektsmelding.toJson(InntektsmeldingV1.serializer()),
                    Key.INNTEKTSMELDING_DOKUMENT to innkommendeMelding.inntektsmelding.tilGammelInntektsmeldingJson(),
                    Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                )

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                    tittel = "Inntektsmelding",
                    gjelderPerson = GjelderPerson(Mock.inntektsmelding.sykmeldt.fnr.verdi),
                    avsender =
                        KlientAvsender.Organisasjon(
                            orgnr = Mock.inntektsmelding.avsender.orgnr.verdi,
                            navn = Mock.inntektsmelding.avsender.orgNavn,
                        ),
                    datoMottatt = LocalDate.now(),
                    dokumenter =
                        withArg {
                            it shouldHaveSize 1
                            it.first().dokumentVarianter.map(DokumentVariant::filtype) shouldContainExactly listOf("XML", "PDFA")
                        },
                    eksternReferanseId = "ARI-${innkommendeMelding.transaksjonId}",
                    callId = "callId_${innkommendeMelding.transaksjonId}",
                )
            }
        }

        test("håndterer klientfeil") {
            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("dette går itj', nei!")

            val selvbestemtId = UUID.randomUUID()

            val innkommendeMelding = Mock.innkommendeMelding(EventName.INNTEKTSMELDING_MOTTATT, Mock.inntektsmelding)

            val innkommendeJsonMap =
                innkommendeMelding
                    .toMap()
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson())

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke journalføre.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        innkommendeJsonMap
                            .plus(
                                Key.BEHOV to BehovType.JOURNALFOER.toJson(),
                            ).toJson(),
                )

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                forventetFail
                    .tilMelding()
                    .minus(Key.FORESPOERSEL_ID)
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson())

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket event" to Pair(Key.EVENT_NAME, EventName.MANUELL_OPPRETT_SAK_REQUESTED.toJson()),
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
                    mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    })

private object Mock {
    val inntektsmelding = mockInntektsmeldingV1()
    val bestemmendeFravaersdag = 20.oktober

    val fail =
        Fail(
            feilmelding = "I don't think we're in Kansas anymore.",
            event = EventName.INNTEKTSMELDING_MOTTATT,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = JsonNull,
        )

    fun innkommendeMelding(
        eventName: EventName,
        inntektsmelding: InntektsmeldingV1,
        bestemmendeFravaersdag: LocalDate? = null,
    ): JournalfoerImMelding =
        JournalfoerImMelding(
            eventName = eventName,
            transaksjonId = UUID.randomUUID(),
            inntektsmelding = inntektsmelding,
            bestemmendeFravaersdag = bestemmendeFravaersdag,
        )

    fun JournalfoerImMelding.toMap(imKey: Key = Key.INNTEKTSMELDING): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            imKey to inntektsmelding.toJson(InntektsmeldingV1.serializer()),
            Key.BESTEMMENDE_FRAVAERSDAG to bestemmendeFravaersdag?.toJson(LocalDateSerializer),
        ).mapValuesNotNull { it }

    fun opprettOgFerdigstillResponse(journalpostId: String): OpprettOgFerdigstillResponse =
        OpprettOgFerdigstillResponse(
            journalpostId = journalpostId,
            journalpostFerdigstilt = true,
            melding = null,
            dokumenter = emptyList(),
        )
}

private fun InntektsmeldingV1.tilGammelInntektsmeldingJson(): JsonElement =
    convert()
        .toJson(Inntektsmelding.serializer())
