package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class InntektsmeldingRegistrertProducerTest {

    @Test
    fun skal_returnere_uuid() {
        val rapidsConnection = mockk<RapidsConnection>()
        val producer = InntektsmeldingRegistrertProducer(rapidsConnection)
        val request = InntektsmeldingRequest("123", "789")
        every {
            rapidsConnection.publish("789", any())
        } returns Unit
        assertNotNull(producer.publish(request))
    }
}
