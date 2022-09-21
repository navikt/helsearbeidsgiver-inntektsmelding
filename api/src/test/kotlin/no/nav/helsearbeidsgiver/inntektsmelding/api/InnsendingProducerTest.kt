package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InntektsmeldingRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class InnsendingProducerTest {

    @Test
    fun skal_returnere_uuid() {
        val rapidsConnection = mockk<RapidsConnection>()
        val producer = InnsendingProducer(rapidsConnection)
        val request = InntektsmeldingRequest("123", "789")
        every {
            rapidsConnection.publish("789", any())
        } returns Unit
        assertNotNull(producer.publish(request))
    }
}
