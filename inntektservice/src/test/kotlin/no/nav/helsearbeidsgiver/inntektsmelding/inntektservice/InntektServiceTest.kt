package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
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
            feilmelding = "ikkeno",
            event = EventName.INNTEKT_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonObject(
                mapOf(
                    Key.BEHOV.str to BehovType.INNTEKT.toJson()
                )
            )
        )
        val transaction = service.onError(feil)
        assertEquals(Transaction.IN_PROGRESS, transaction)

        service.terminate(feil) // skal ikke kaste exception..
    }
}
