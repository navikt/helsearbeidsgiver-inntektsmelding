package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.utils.json.fromJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InntektServiceTest {

    private val testRapid = TestRapid()
    private val mockRedis = MockRedis()

    private val service = spyk(
        InntektService(testRapid, mockRedis.store)
    )

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `ukritisk feil stopper ikke flyten`() {
        val event = EventName.INNTEKT_REQUESTED
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()

        every { mockRedis.store.get(RedisKey.of(transaksjonId, event)) } returns clientId.toString()
        every { service.inProgress(any()) } just Runs

        val fail = Fail(
            feilmelding = "ikkeno",
            event = event,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = JsonObject(
                mapOf(
                    Key.BEHOV.str to BehovType.INNTEKT.toJson()
                )
            )
        )

        shouldNotThrowAny {
            service.onError(JsonMessage.newMessage(), fail)
        }

        verify {
            service.inProgress(any())
        }

        verify(exactly = 0) {
            mockRedis.store.set(
                RedisKey.of(clientId),
                withArg {
                    runCatching {
                        it.fromJson(FeilReport.serializer())
                    }
                        .getOrNull()
                        .shouldBeNull()
                }
            )
        }
    }
}
