package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingProducer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class InnsendingProducerTest {

    @Test
    fun skal_returnere_uuid() {
        val rapidsConnection = mockk<RapidsConnection>()
        val producer = InnsendingProducer(rapidsConnection)
        every {
            rapidsConnection.publish(any())
        } returns Unit
        assertNotNull(producer.publish(UUID.randomUUID(), GYLDIG_INNSENDING_REQUEST, "gyldig-fnr"))
    }
}
