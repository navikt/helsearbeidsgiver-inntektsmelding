package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class OpprettSakServiceTest {

    val testRapid = TestRapid()
    val testRedis = MockRedisStore()

    @Test
    fun `OpprettSak skal håndtere feil`() {
        val foresporselId = UUID.randomUUID()
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.SAK_OPPRETT_REQUESTED.name,
                DataFelt.ORGNRUNDERENHET.str to "123456",
                Key.IDENTITETSNUMMER.str to "123456789",
                Key.FORESPOERSEL_ID.str to foresporselId
            )
        )

        OpprettSakService(testRapid, testRedis)

        testRapid.sendTestMessage(
            message.toJson()
        )
        val uuid = testRedis.get("uuid").orEmpty().let(UUID::fromString)
        println("Fant uuid: $uuid")
        assertNotNull(testRedis.get(RedisKey.of(uuid, EventName.SAK_OPPRETT_REQUESTED)))
        val feilmelding = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.SAK_OPPRETT_REQUESTED.name,
                Key.FORESPOERSEL_ID.str to foresporselId,
                Key.UUID.str to uuid,
                Key.FAIL.str to
                    Behov.create(
                        EventName.SAK_OPPRETT_REQUESTED,
                        BehovType.FULLT_NAVN,
                        foresporselId.toString(),
                        mapOf(Key.UUID to uuid)
                    )
                        .createFail(
                            "Klarte ikke hente navn"
                        )
                // Key.FAIL.str to "Klarte ikke hente navn" -> Dette knekker StatefullEventListener.isFailMelding()
            )
        )
        testRapid.sendTestMessage(
            feilmelding.toJson()
        )
        assertNotNull(testRedis.get(RedisKey.of(uuid, DataFelt.ARBEIDSTAKER_INFORMASJON)))
    }
}
