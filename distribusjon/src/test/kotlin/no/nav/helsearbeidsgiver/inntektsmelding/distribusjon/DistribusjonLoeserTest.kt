package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DistribusjonLoeserTest {

    private val rapid = TestRapid()
    private var løser: DistribusjonLoeser
    private val kafkaProducer = mockk<KafkaProducer<String, String>>()
    private val JOURNALPOST_ID = "12345"

    init {
        løser = DistribusjonLoeser(rapid, kafkaProducer)
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
    }

    @Test
    fun `skal distribuere ekstern og internt`() {
        coEvery {
            kafkaProducer.send(any())
        } returns CompletableFuture()
        rapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.BEHOV to BehovType.DISTRIBUER_IM.toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.JOURNALPOST_ID to JOURNALPOST_ID.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to mockInntektsmelding().toJson(Inntektsmelding.serializer())
        )
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding er distribuert")
        assertEquals(EventName.INNTEKTSMELDING_DISTRIBUERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertEquals(JOURNALPOST_ID, melding.get(Key.JOURNALPOST_ID.str).asText())
        assertNotNull(melding.get(Key.INNTEKTSMELDING_DOKUMENT.str))
        assertNull(melding.get(Key.FAIL.str), "Skal ikke inneholde feil")
    }

    @Test
    fun `skal håndtere feil ved deserialisering`() {
        coEvery {
            kafkaProducer.send(any())
        } returns CompletableFuture()
        rapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.BEHOV to BehovType.DISTRIBUER_IM.toJson(),
            Key.JOURNALPOST_ID to JOURNALPOST_ID.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to "dummy".toJson()
        )
        val publisert = rapid.firstMessage().readFail()

        publisert.event shouldBe EventName.INNTEKTSMELDING_JOURNALFOERT
    }

    @Test
    fun `skal håndtere feil ved publisering på ekstern kafka topic`() {
        coEvery {
            kafkaProducer.send(any())
        } throws Exception("")
        rapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.BEHOV to BehovType.DISTRIBUER_IM.toJson(),
            Key.JOURNALPOST_ID to JOURNALPOST_ID.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to "dummy".toJson()
        )

        val publisert = rapid.firstMessage().readFail()

        publisert.event shouldBe EventName.INNTEKTSMELDING_JOURNALFOERT
    }

    @AfterEach
    fun cleanPrometheusMetrics() {
        CollectorRegistry.defaultRegistry.clear()
    }
}
