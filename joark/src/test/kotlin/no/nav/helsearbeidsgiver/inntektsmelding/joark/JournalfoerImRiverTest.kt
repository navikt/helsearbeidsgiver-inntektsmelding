package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.time.LocalDate
import java.util.UUID
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender as KlientAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1

class JournalfoerImRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockDokArkivKlient = mockk<DokArkivClient>()

    JournalfoerImRiver(mockDokArkivKlient).connect(testRapid)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
    }

    context("oppretter journalpost og publiserer melding for å lagre journalpost-ID") {
        withData(
            mapOf(
                "forespurt inntektmelding (gammel versjon)" to Mock.inntektmeldingGammelVersjon.toJson(Inntektsmelding.serializer()),
                "forespurt inntektmelding (ny versjon, ikke i bruk)" to mockInntektsmeldingV1().toJson(InntektsmeldingV1.serializer())
            )
        ) { inntektsmeldingJson ->
            val journalpostId = UUID.randomUUID().toString()

            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            } returns Mock.opprettOgFerdigstillResponse(journalpostId)

            val innkommendeMelding = JournalfoerImMelding(
                eventName = EventName.INNTEKTSMELDING_MOTTATT,
                transaksjonId = UUID.randomUUID(),
                inntektsmeldingJson = inntektsmeldingJson
            )

            val forespoerselId = UUID.randomUUID()

            testRapid.sendJson(
                innkommendeMelding.tilMap()
                    .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())
            )

            val publisert = testRapid.firstMessage().toMap()

            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
            Key.BEHOV.lesOrNull(BehovType.serializer(), publisert) shouldBe BehovType.LAGRE_JOURNALPOST_ID
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
            Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert) shouldBe journalpostId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId
            Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

            publisert[Key.DATA].shouldBeNull()
            publisert[Key.FAIL].shouldBeNull()

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                    tittel = "Inntektsmelding",
                    gjelderPerson = GjelderPerson(Mock.inntektmeldingGammelVersjon.identitetsnummer),
                    avsender = KlientAvsender.Organisasjon(
                        orgnr = Mock.inntektmeldingGammelVersjon.orgnrUnderenhet,
                        navn = Mock.inntektmeldingGammelVersjon.virksomhetNavn
                    ),
                    datoMottatt = LocalDate.now(),
                    dokumenter = withArg {
                        it shouldHaveSize 1
                        it.first().dokumentVarianter.map { it.filtype } shouldContainExactly listOf("XML", "PDFA")
                    },
                    eksternReferanseId = "ARI-${innkommendeMelding.transaksjonId}",
                    callId = "callId_${innkommendeMelding.transaksjonId}"
                )
            }
        }

        withData(
            mapOf(
                "selvbestemt inntektmelding (ny versjon)" to mockInntektsmeldingV1().toJson(InntektsmeldingV1.serializer()),
                "selvbestemt inntektmelding (gammel versjon, ikke i bruk)" to Mock.inntektmeldingGammelVersjon.toJson(Inntektsmelding.serializer())
            )
        ) { inntektsmeldingJson ->
            val journalpostId = UUID.randomUUID().toString()
            val inntektsmeldingNyVersjon = mockInntektsmeldingV1()

            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            } returns Mock.opprettOgFerdigstillResponse(journalpostId)

            val innkommendeMelding = JournalfoerImMelding(
                eventName = EventName.AAPEN_IM_LAGRET,
                transaksjonId = UUID.randomUUID(),
                inntektsmeldingJson = inntektsmeldingJson
            )

            val aapenId = UUID.randomUUID()

            testRapid.sendJson(
                innkommendeMelding.tilMap(Key.AAPEN_INNTEKTMELDING)
                    .plus(Key.AAPEN_ID to aapenId.toJson())
            )

            val publisert = testRapid.firstMessage().toMap()

            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
            Key.BEHOV.lesOrNull(BehovType.serializer(), publisert) shouldBe BehovType.LAGRE_JOURNALPOST_ID
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
            Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert) shouldBe journalpostId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()
            Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert) shouldBe aapenId

            publisert[Key.DATA].shouldBeNull()
            publisert[Key.FAIL].shouldBeNull()

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(
                    tittel = "Inntektsmelding",
                    gjelderPerson = GjelderPerson(inntektsmeldingNyVersjon.sykmeldt.fnr),
                    avsender = KlientAvsender.Organisasjon(
                        orgnr = inntektsmeldingNyVersjon.avsender.orgnr,
                        navn = inntektsmeldingNyVersjon.avsender.orgNavn
                    ),
                    datoMottatt = LocalDate.now(),
                    dokumenter = withArg {
                        it shouldHaveSize 1
                        it.first().dokumentVarianter.map(DokumentVariant::filtype) shouldContainExactly listOf("XML", "PDFA")
                    },
                    eksternReferanseId = "ARI-${innkommendeMelding.transaksjonId}",
                    callId = "callId_${innkommendeMelding.transaksjonId}"
                )
            }
        }
    }

    context("håndterer feil") {
        test("ugyldig inntektsmelding feiler") {
            val forespoerselId = UUID.randomUUID()

            val innkommendeMelding = JournalfoerImMelding(
                eventName = EventName.INNTEKTSMELDING_MOTTATT,
                transaksjonId = UUID.randomUUID(),
                inntektsmeldingJson = "ikke en inntektsmelding".toJson()
            )

            val innkommendeJsonMap = innkommendeMelding.tilMap()
                .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())

            val forventetFail = Fail(
                feilmelding = "Klarte ikke journalføre.",
                event = innkommendeMelding.eventName,
                transaksjonId = innkommendeMelding.transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = innkommendeJsonMap.toJson()
            )

            testRapid.sendJson(innkommendeJsonMap)

            val publisert = testRapid.firstMessage().toMap()

            Key.FAIL.lesOrNull(Fail.serializer(), publisert) shouldBe forventetFail
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId

            publisert[Key.BEHOV].shouldBeNull()
            publisert[Key.DATA].shouldBeNull()

            coVerify(exactly = 0) {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("klient feiler") {
            coEvery {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("dette går itj', nei!")

            val forespoerselId = UUID.randomUUID()

            val innkommendeMelding = JournalfoerImMelding(
                eventName = EventName.INNTEKTSMELDING_MOTTATT,
                transaksjonId = UUID.randomUUID(),
                inntektsmeldingJson = Mock.inntektmeldingGammelVersjon.toJson(Inntektsmelding.serializer())
            )

            val innkommendeJsonMap = innkommendeMelding.tilMap()
                .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())

            val forventetFail = Fail(
                feilmelding = "Klarte ikke journalføre.",
                event = innkommendeMelding.eventName,
                transaksjonId = innkommendeMelding.transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = innkommendeJsonMap.toJson()
            )

            testRapid.sendJson(innkommendeJsonMap)

            val publisert = testRapid.firstMessage().toMap()

            Key.FAIL.lesOrNull(Fail.serializer(), publisert) shouldBe forventetFail
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId

            publisert[Key.BEHOV].shouldBeNull()
            publisert[Key.DATA].shouldBeNull()

            coVerifySequence {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    context("ignorerer melding") {
        withData(
            mapOf(
                "melding med behov" to Pair(Key.BEHOV, BehovType.VIRKSOMHET.toJson()),
                "melding med data" to Pair(Key.DATA, "".toJson()),
                "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer()))
            )
        ) { uoensketKeyMedVerdi ->
            val innkommendeMelding = JournalfoerImMelding(
                eventName = EventName.INNTEKTSMELDING_MOTTATT,
                transaksjonId = UUID.randomUUID(),
                inntektsmeldingJson = Mock.inntektmeldingGammelVersjon.toJson(Inntektsmelding.serializer())
            )

            testRapid.sendJson(
                innkommendeMelding.tilMap()
                    .plus(uoensketKeyMedVerdi)
            )

            testRapid.inspektør.size shouldBe 0

            coVerify(exactly = 0) {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("melding med uønsket event") {
            val innkommendeMelding = JournalfoerImMelding(
                eventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED,
                transaksjonId = UUID.randomUUID(),
                inntektsmeldingJson = Mock.inntektmeldingGammelVersjon.toJson(Inntektsmelding.serializer())
            )

            testRapid.sendJson(innkommendeMelding.tilMap())

            testRapid.inspektør.size shouldBe 0

            coVerify(exactly = 0) {
                mockDokArkivKlient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }
})

private fun JournalfoerImMelding.tilMap(imKey: Key = Key.INNTEKTSMELDING_DOKUMENT): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.UUID to transaksjonId.toJson(),
        imKey to inntektsmeldingJson.toJson(JsonElement.serializer())
    )

private object Mock {
    val inntektmeldingGammelVersjon = mockInntektsmeldingV1().convert()

    val fail = Fail(
        feilmelding = "I don't think we're in Kansas anymore.",
        event = EventName.INNTEKTSMELDING_MOTTATT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull
    )

    fun opprettOgFerdigstillResponse(journalpostId: String): OpprettOgFerdigstillResponse =
        OpprettOgFerdigstillResponse(
            journalpostId = journalpostId,
            journalpostFerdigstilt = true,
            melding = null,
            dokumenter = emptyList()
        )
}
