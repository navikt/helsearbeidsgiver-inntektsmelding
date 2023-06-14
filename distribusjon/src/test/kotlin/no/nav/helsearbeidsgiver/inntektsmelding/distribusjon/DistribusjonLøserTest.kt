package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class DistribusjonLøserTest {

    private val rapid = TestRapid()
    private var løser: DistribusjonLøser
    private val kafkaProducer = mockk<KafkaProducer<String, String>>()
    private val om = customObjectMapper()
    private val JOURNALPOST_ID = "12345"
    private val F_ID = "123"

    init {
        løser = DistribusjonLøser(rapid, kafkaProducer)
    }

    @Test
    fun `skal distribuere ekstern og internt`() {
        coEvery {
            kafkaProducer.send(any())
        } returns CompletableFuture()

        val mld = mapOf(
            Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
            Key.BEHOV.str to BehovType.DISTRIBUER_IM.name,
            Key.JOURNALPOST_ID.str to JOURNALPOST_ID,
            Key.INNTEKTSMELDING_DOKUMENT.str to mockInntektsmeldingDokument()
            Key.FORESPOERSEL_ID.str to F_ID
        )
        sendMelding(mld)
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding er distribuert")
        assertEquals(EventName.INNTEKTSMELDING_DISTRIBUERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertEquals(F_ID, melding.get(Key.FORESPOERSEL_ID.str).asText())
        assertEquals(JOURNALPOST_ID, melding.get(Key.JOURNALPOST_ID.str).asText())
        assertNotNull(melding.get(Key.INNTEKTSMELDING_DOKUMENT.str))
        assertNull(melding.get(Key.FAIL.str), "Skal ikke inneholde feil")
    }

    @Test
    fun `skal håndtere feil ved deserialisering`() {
        coEvery {
            kafkaProducer.send(any())
        } returns CompletableFuture()
        val mld = mapOf(
            Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
            Key.BEHOV.str to BehovType.DISTRIBUER_IM.name,
            Key.JOURNALPOST_ID.str to JOURNALPOST_ID,
            Key.INNTEKTSMELDING_DOKUMENT.str to "dummy"
            Key.FORESPOERSEL_ID.str to F_ID
        )
        sendMelding(mld)
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding IKKE ble distribuert")
        assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertEquals(F_ID, melding.get(Key.FORESPOERSEL_ID.str).asText())
        assertNotNull(melding.get(Key.FAIL.str).asText(), "Skal inneholde feil")
    }

    @Test
    fun `skal håndtere feil ved publisering på ekstern kafka topic`() {
        coEvery {
            kafkaProducer.send(any())
        } throws Exception("")
        val mld = mapOf(
            Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
            Key.BEHOV.str to BehovType.DISTRIBUER_IM.name,
            Key.JOURNALPOST_ID.str to JOURNALPOST_ID,
            Key.INNTEKTSMELDING_DOKUMENT.str to "dummy"
            Key.FORESPOERSEL_ID.str to F_ID
        )
        sendMelding(mld)
        val melding = rapid.inspektør.message(0)
        assertNotNull(melding, "Skal publisere event at inntektsmelding IKKE ble distribuert")
        assertEquals(EventName.INNTEKTSMELDING_JOURNALFOERT.name, melding.get(Key.EVENT_NAME.str).asText())
        assertEquals(F_ID, melding.get(Key.FORESPOERSEL_ID.str).asText())
        assertNotNull(melding.get(Key.FAIL.str).asText(), "Skal inneholde feil")
    }

    private fun sendMelding(melding: Map<String, Any>) {
        rapid.reset()
        rapid.sendTestMessage(om.writeValueAsString(melding))
    }
}
