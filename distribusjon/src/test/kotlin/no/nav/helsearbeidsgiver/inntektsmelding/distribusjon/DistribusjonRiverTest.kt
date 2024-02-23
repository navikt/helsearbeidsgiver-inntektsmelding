package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.JournalfoertInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DistribusjonRiverTest : FunSpec({

    val testRapid = TestRapid()
    val mockKafkaProducer = mockk<KafkaProducer<String, String>>()

    DistribusjonRiver(mockKafkaProducer).connect(testRapid)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
    }

    test("distribuerer inntektsmelding på kafka topic") {
        every { mockKafkaProducer.send(any()) } returns CompletableFuture()

        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()
        val journalfoertInntektsmelding = JournalfoertInntektsmelding(
            journalpostId = "7983693",
            inntektsmelding = mockInntektsmelding()
        )

        val innkommendeMelding = Melding(
            eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
            transaksjonId = transaksjonId,
            journalpostId = journalfoertInntektsmelding.journalpostId,
            inntektsmelding = journalfoertInntektsmelding.inntektsmelding
        )

        testRapid.sendJson(
            innkommendeMelding.tilMap()
                .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_DISTRIBUERT
        Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe transaksjonId
        Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert) shouldBe journalfoertInntektsmelding.journalpostId
        Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), publisert) shouldBe journalfoertInntektsmelding.inntektsmelding
        Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId
        Key.AAPEN_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

        val forventetRecord = ProducerRecord<String, String>(
            TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
            journalfoertInntektsmelding.toJsonStr(JournalfoertInntektsmelding.serializer())
        )

        verifySequence {
            mockKafkaProducer.send(forventetRecord)
        }
    }

    test("håndterer når producer feiler") {
        every { mockKafkaProducer.send(any()) } throws RuntimeException("feil og feil, fru blom")

        val journalpostId = "6468749"
        val forespoerselId = UUID.randomUUID()

        val innkommendeMelding = Melding(
            eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
            transaksjonId = UUID.randomUUID(),
            journalpostId = journalpostId,
            inntektsmelding = mockInntektsmelding()
        )

        val innkommendeJsonMap = innkommendeMelding.tilMap()
            .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())

        val forventetFail = Fail(
            feilmelding = "Klarte ikke distribuere IM med journalpost-ID: '$journalpostId'.",
            event = innkommendeMelding.eventName,
            transaksjonId = innkommendeMelding.transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = innkommendeJsonMap.toJson()
        )

        testRapid.sendJson(innkommendeJsonMap)

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        Key.FAIL.lesOrNull(Fail.serializer(), publisert) shouldBe forventetFail
        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
        Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
        Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert) shouldBe forespoerselId

        verifySequence {
            mockKafkaProducer.send(any())
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
            val innkommendeMelding = Melding(
                eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
                transaksjonId = UUID.randomUUID(),
                journalpostId = "154698234",
                inntektsmelding = mockInntektsmelding()
            )

            testRapid.sendJson(
                innkommendeMelding.tilMap()
                    .plus(uoensketKeyMedVerdi)
            )

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockKafkaProducer.send(any())
            }
        }

        test("melding med ukjent event") {
            val innkommendeMelding = Melding(
                eventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED,
                transaksjonId = UUID.randomUUID(),
                journalpostId = "90460",
                inntektsmelding = mockInntektsmelding()
            )

            testRapid.sendJson(innkommendeMelding.tilMap())

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockKafkaProducer.send(any())
            }
        }
    }
})

private fun Melding.tilMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.JOURNALPOST_ID to journalpostId.toJson(),
        Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer())
    )

private object Mock {
    val fail = Fail(
        feilmelding = "I'm afraid I can't let you do that.",
        event = EventName.INNTEKTSMELDING_JOURNALFOERT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull
    )
}
