package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.result.shouldBeSuccess
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.utils.json.fromJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        val fail =
            mockFail(
                feilmelding = "ikkeno",
                eventName = EventName.INNTEKT_REQUESTED,
                behovType = BehovType.HENT_INNTEKT,
            )

        shouldNotThrowAny {
            service.onError(emptyMap(), fail)
        }

        verify {
            mockRedis.store.skrivResultat(
                fail.kontekstId,
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
