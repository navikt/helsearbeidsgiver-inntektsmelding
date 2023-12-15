package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class OpprettSakServiceTest {

    private val testRapid = TestRapid()
    private val mockRedis = MockRedis()

    init {
        OpprettSakService(testRapid, mockRedis.store)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `OpprettSak skal håndtere feil`() {
        val transaksjonId: UUID = UUID.randomUUID()
        val foresporselId: UUID = UUID.randomUUID()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.name.toJson(),
                Key.CLIENT_ID to UUID.randomUUID().toJson(),
                Key.FORESPOERSEL_ID to foresporselId.toJson(),
                Key.ORGNRUNDERENHET to "123456".toJson(),
                Key.IDENTITETSNUMMER to "123456789".toJson()
            )
        }

        testRapid.sendJson(
            Key.FAIL to Fail(
                feilmelding = "Klarte ikke hente navn",
                event = EventName.SAK_OPPRETT_REQUESTED,
                transaksjonId = transaksjonId,
                forespoerselId = foresporselId,
                utloesendeMelding = JsonObject(
                    mapOf(
                        Key.BEHOV.str to BehovType.FULLT_NAVN.toJson()
                    )
                )
            ).toJson(Fail.serializer()),
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to foresporselId.toJson()
        )

        verify {
            mockRedis.store.set(
                RedisKey.of(transaksjonId, Key.ARBEIDSTAKER_INFORMASJON),
                PersonDato("Ukjent person", null, "").toJsonStr(PersonDato.serializer())
            )
        }
    }
}
