package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
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
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.distribusjon.Mock.toMap
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
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

    context("distribuerer inntektsmelding på kafka topic") {
        withData(
            mapOf(
                "for vanlig melding" to null,
                "for retry-melding" to BehovType.DISTRIBUER_IM
            )
        ) { innkommendeBehov ->

            every { mockKafkaProducer.send(any()) } returns CompletableFuture()

            val forespoerselId = UUID.randomUUID()

            val innkommendeMelding = Mock.innkommendeMelding()

            testRapid.sendJson(
                innkommendeMelding.toMap()
                    .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())
                    .plus(Key.BEHOV to innkommendeBehov?.toJson())
                    .mapValuesNotNull { it }
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly mapOf(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_DISTRIBUERT.toJson(),
                Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                Key.JOURNALPOST_ID to innkommendeMelding.journalpostId.toJson(),
                Key.INNTEKTSMELDING_DOKUMENT to innkommendeMelding.inntektsmelding.toJson(Inntektsmelding.serializer()),
                Key.FORESPOERSEL_ID to forespoerselId.toJson()
            )

            val forventetRecord = ProducerRecord<String, String>(
                TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                JournalfoertInntektsmelding(
                    journalpostId = innkommendeMelding.journalpostId,
                    inntektsmelding = innkommendeMelding.inntektsmelding,
                    selvbestemt = false
                ).toJsonStr(JournalfoertInntektsmelding.serializer())
            )

            verifySequence {
                mockKafkaProducer.send(forventetRecord)
            }
        }
    }

    context("distribuerer selvbestemt inntektsmelding på kafka topic") {
        withData(
            mapOf(
                "for vanlig melding" to null,
                "for retry-melding" to BehovType.DISTRIBUER_IM
            )
        ) { innkommendeBehov ->

            every { mockKafkaProducer.send(any()) } returns CompletableFuture()

            val transaksjonId = UUID.randomUUID()
            val selvbestemtId = UUID.randomUUID()
            val journalfoertInntektsmelding = JournalfoertInntektsmelding(
                journalpostId = "7983693",
                inntektsmelding = mockInntektsmelding(),
                selvbestemt = true
            )

            val innkommendeMelding = Melding(
                eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
                transaksjonId = transaksjonId,
                journalpostId = journalfoertInntektsmelding.journalpostId,
                inntektsmelding = journalfoertInntektsmelding.inntektsmelding
            )

            testRapid.sendJson(
                innkommendeMelding.toMap()
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson())
                    .plus(Key.BEHOV to innkommendeBehov?.toJson())
                    .mapValuesNotNull { it }
            )

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_DISTRIBUERT
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe transaksjonId
            Key.JOURNALPOST_ID.lesOrNull(String.serializer(), publisert) shouldBe journalfoertInntektsmelding.journalpostId
            Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), publisert) shouldBe journalfoertInntektsmelding.inntektsmelding
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()
            Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, publisert) shouldBe selvbestemtId

            val forventetRecord = ProducerRecord<String, String>(
                TOPIC_HELSEARBEIDSGIVER_INNTEKTSMELDING_EKSTERN,
                journalfoertInntektsmelding.toJsonStr(JournalfoertInntektsmelding.serializer())
            )

            verifySequence {
                mockKafkaProducer.send(forventetRecord)
            }
        }
    }
    test("håndterer når producer feiler") {
        every { mockKafkaProducer.send(any()) } throws RuntimeException("feil og feil, fru blom")

        val forespoerselId = UUID.randomUUID()

        val innkommendeMelding = Mock.innkommendeMelding()

        val innkommendeJsonMap = innkommendeMelding.toMap()
            .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())

        val forventetFail = Fail(
            feilmelding = "Klarte ikke distribuere IM med journalpost-ID: '${innkommendeMelding.journalpostId}'.",
            event = innkommendeMelding.eventName,
            transaksjonId = innkommendeMelding.transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = innkommendeJsonMap.plus(
                Key.BEHOV to BehovType.DISTRIBUER_IM.toJson()
            )
                .toJson()
        )

        testRapid.sendJson(innkommendeJsonMap)

        testRapid.inspektør.size shouldBeExactly 1

        testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()
            .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())

        verifySequence {
            mockKafkaProducer.send(any())
        }
    }

    context("ignorerer melding") {
        withData(
            mapOf(
                "melding med ukjent event" to Pair(Key.EVENT_NAME, EventName.MANUELL_OPPRETT_SAK_REQUESTED.toJson()),
                "melding med behov" to Pair(Key.BEHOV, BehovType.VIRKSOMHET.toJson()),
                "melding med data" to Pair(Key.DATA, "".toJson()),
                "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer()))
            )
        ) { uoensketKeyMedVerdi ->
            testRapid.sendJson(
                Mock.innkommendeMelding().toMap()
                    .plus(uoensketKeyMedVerdi)
            )

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockKafkaProducer.send(any())
            }
        }
    }
})

private object Mock {
    val fail = Fail(
        feilmelding = "I'm afraid I can't let you do that.",
        event = EventName.INNTEKTSMELDING_JOURNALFOERT,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding = JsonNull
    )

    fun innkommendeMelding(): Melding =
        Melding(
            eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
            transaksjonId = UUID.randomUUID(),
            journalpostId = randomDigitString(13),
            inntektsmelding = mockInntektsmelding()
        )

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer())
        )
}
