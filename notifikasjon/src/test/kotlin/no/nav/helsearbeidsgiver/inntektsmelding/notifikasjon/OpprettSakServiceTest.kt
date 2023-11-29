package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
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
        val uuid = UUID.randomUUID()
        val foresporselId = UUID.randomUUID()
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.SAK_OPPRETT_REQUESTED.name,
                Key.CLIENT_ID.str to UUID.randomUUID(),
                Key.ORGNRUNDERENHET.str to "123456",
                Key.IDENTITETSNUMMER.str to "123456789",
                Key.FORESPOERSEL_ID.str to foresporselId
            )
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns uuid

            testRapid.sendTestMessage(
                message.toJson()
            )
        }

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.FORESPOERSEL_ID to foresporselId.toJson(),
            Key.UUID to uuid.toJson(),
            Key.FAIL to Behov.create(
                event = EventName.SAK_OPPRETT_REQUESTED,
                behov = BehovType.FULLT_NAVN,
                forespoerselId = foresporselId.toString(),
                map = mapOf(Key.UUID to uuid)
            )
                .createFail("Klarte ikke hente navn")
                .toJson(Fail.serializer())
        )

        verify {
            mockRedis.store.set(
                RedisKey.of(uuid, Key.ARBEIDSTAKER_INFORMASJON),
                PersonDato("Ukjent person", null, "").toJsonStr(PersonDato.serializer())
            )
        }
    }
}
