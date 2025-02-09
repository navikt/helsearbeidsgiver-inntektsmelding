package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.TilgangForespoerselService
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TilgangForespoerselServiceTest {
    private val testRapid = TestRapid()
    private val mockRedisStore = mockk<RedisStore>(relaxed = true)

    private val service = TilgangForespoerselService(testRapid, mockRedisStore)

    init {
        ServiceRiverStateless(service).connect(testRapid)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `kritisk feil stopper flyten`() {
        val fail =
            mockFail(
                feilmelding = "ikkeno",
                eventName = EventName.TILGANG_FORESPOERSEL_REQUESTED,
                behovType = BehovType.TILGANGSKONTROLL,
            )

        shouldNotThrowAny {
            service.onError(emptyMap(), fail)
        }

        val expectedResult =
            ResultJson(
                failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(),
            )

        verify {
            mockRedisStore.skrivResultat(fail.kontekstId, expectedResult)
        }
    }
}
