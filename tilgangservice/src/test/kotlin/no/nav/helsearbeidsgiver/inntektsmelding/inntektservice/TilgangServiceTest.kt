package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.TilgangService
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class TilgangServiceTest {

    private val testRapid = TestRapid()
    private val mockRedis = MockRedis()

    private val service = TilgangService(testRapid, mockRedis.store)

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `kritisk feil stopper flyten`() {
        // TODO: uuid *må* være satt i Fail - ellers kastes IllegalStateException ved onError()
        // Kunne endret dette til å returnere Transaction.TERMINATE, men da gjenoppstår
        // problemet som en IllegalArgumentException i .terminate()
        // Kanskje bør uuid enforces til ikke-null i Fail?

        val event = EventName.TILGANG_REQUESTED
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()

        every { mockRedis.store.get(RedisKey.of(transaksjonId, event)) } returns clientId.toString()

        val fail = Fail(
            feilmelding = "ikkeno",
            event = event,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = JsonObject(
                mapOf(
                    Key.BEHOV.str to BehovType.TILGANGSKONTROLL.toJson()
                )
            )
        )

        shouldNotThrowAny {
            service.onError(emptyMap(), fail)
        }

        val expectedFeilReport = FeilReport(
            mutableListOf(
                Feilmelding("Teknisk feil, prøv igjen senere.", -1, Key.TILGANG)
            )
        )
            .toJsonStr(FeilReport.serializer())

        verify {
            mockRedis.store.set(RedisKey.of(clientId), expectedFeilReport)
        }
    }
}
