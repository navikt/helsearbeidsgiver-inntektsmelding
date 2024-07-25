package no.nav.helsearbeidsgiver.inntektsmelding.inntektservice

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tekst
import no.nav.helsearbeidsgiver.felles.TilgangResultat
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.inntektsmelding.tilgangservice.TilgangOrgService
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class TilgangOrgServiceTest {
    private val testRapid = TestRapid()
    private val mockRedis = MockRedis(RedisPrefix.TilgangOrg)

    private val service = TilgangOrgService(testRapid, mockRedis.store)

    init {
        ServiceRiverStateful(mockRedis.store, service).connect(testRapid)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `kritisk feil stopper flyten`() {
        val event = EventName.TILGANG_ORG_REQUESTED
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
                            Key.BEHOV.str to BehovType.TILGANGSKONTROLL.toJson(),
                        ),
                    ),
            )

        shouldNotThrowAny {
            service.onError(emptyMap(), fail)
        }

        val expectedResultJson =
            TilgangResultat(
                feilmelding = Tekst.TEKNISK_FEIL_FORBIGAAENDE,
            ).toJson(TilgangResultat.serializer())

        verify {
            mockRedis.store.set(RedisKey.of(transaksjonId), expectedResultJson)
        }
    }
}
