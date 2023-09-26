package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
            Key.JOURNALPOST_ID to JOURNALPOST_ID.toJson(),
            DataFelt.INNTEKTSMELDING_DOKUMENT to mockInntektsmeldingDokument().let(Jackson::toJson).parseJson()
        )
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding er distribuert")
        assertEquals(EventName.INNTEKTSMELDING_DISTRIBUERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertEquals(JOURNALPOST_ID, melding.get(Key.JOURNALPOST_ID.str).asText())
        assertNotNull(melding.get(DataFelt.INNTEKTSMELDING_DOKUMENT.str))
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
            DataFelt.INNTEKTSMELDING_DOKUMENT to "dummy".toJson()
        )
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding IKKE ble distribuert")
        assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertNotNull(melding.get(Key.FAIL.str).asText(), "Skal inneholde feil")
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
            DataFelt.INNTEKTSMELDING_DOKUMENT to "dummy".toJson()
        )
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding IKKE ble distribuert")
        assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertNotNull(melding.get(Key.FAIL.str).asText(), "Skal inneholde feil")
    }

    @AfterEach
    fun cleanPrometheusMetrics() {
        CollectorRegistry.defaultRegistry.clear()
    }
}
