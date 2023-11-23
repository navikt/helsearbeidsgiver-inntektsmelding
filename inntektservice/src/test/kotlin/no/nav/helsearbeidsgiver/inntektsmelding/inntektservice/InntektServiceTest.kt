package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InntektServiceTest {

    private val testRapid = TestRapid()
    private val mockRedis = MockRedis()

    @Test
    fun terminate() {
        val service = InntektService(testRapid, mockRedis.store)
        val feil = Fail(
            eventName = EventName.INNTEKT_REQUESTED,
            behov = BehovType.INNTEKT,
            feilmelding = "ikkeno",
            data = null,
            uuid = UUID.randomUUID().toString(),
            forespørselId = null
        )
        val transaction = service.onError(feil)
        assertEquals(Transaction.IN_PROGRESS, transaction)
        service.terminate(feil) // skal ikke kaste exception..
    }
}
