package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.result.shouldBeSuccess
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.utils.json.fromJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class InntektServiceTest {
    private val testRapid = TestRapid()
    private val mockRedis = MockRedis(RedisPrefix.Inntekt)

    private val service =
        spyk(
            InntektService(testRapid, mockRedis.store),
        )

    init {
        ServiceRiverStateless(service).connect(testRapid)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `svarer med feilmelding ved feil under henting av inntekt`() {
        val event = EventName.INNTEKT_REQUESTED
        val transaksjonId = UUID.randomUUID()

        val fail =
            Fail(
                feilmelding = "ikkeno",
                event = event,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding =
                    JsonObject(
                        mapOf(
                            Key.BEHOV.toString() to BehovType.HENT_INNTEKT.toJson(),
                        ),
                    ),
            )

        shouldNotThrowAny {
            service.onError(emptyMap(), fail)
        }

        verify {
            mockRedis.store.skrivResultat(
                transaksjonId,
                withArg {
                    runCatching {
                        it
                            .failure
                            ?.fromJson(String.serializer())
                    }.shouldBeSuccess()
                },
            )
        }
    }
}
