package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.TilgangService
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TilgangServiceTest {

    val mockRedisStore = MockRedisStore()
    val testRapid = TestRapid()

    @Test
    fun testOnError() {
        // TODO: uuid *må* være satt i Fail - ellers kastes IllegalStateException ved onError()
        // Kunne endret dette til å returnere Transaction.TERMINATE, men da gjenoppstår
        // problemet som en IllegalArgumentException i .terminate()
        // Kanskje bør uuid enforces til ikke-null i Fail?

        val service = TilgangService(testRapid, mockRedisStore)
        val feil = Fail(
            feilmelding = "ikkeno",
            event = EventName.TILGANG_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonObject(
                mapOf(
                    Key.BEHOV.str to BehovType.TILGANGSKONTROLL.toJson()
                )
            )
        )
        val transaction = service.onError(feil)
        assertTrue(transaction is Transaction.Terminate)

        service.terminate(feil)
    }
}
