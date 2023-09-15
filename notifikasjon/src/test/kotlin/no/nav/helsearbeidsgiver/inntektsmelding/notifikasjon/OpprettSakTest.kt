package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OpprettSakTest {

    val testRapid = TestRapid()
    val testRedis = MockRedisStore()

    @Test
    fun `OpprettSak skal håndtere feil`() {
        val foresporselId = UUID.randomUUID()
        val message = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET.name,
                DataFelt.ORGNRUNDERENHET.str to "123456",
                Key.IDENTITETSNUMMER.str to "123456789",
                Key.FORESPOERSEL_ID.str to foresporselId
            )
        )

        val os = OpprettSak(testRapid, testRedis)
        val minFailKanal: FailKanal = mockk()
        every { minFailKanal.onFail(any()) } returns Unit

        testRapid.sendTestMessage(
            message.toJson()
        )
        val uuid = testRedis.get("uuid").orEmpty()
        testRedis.set("uuid2", "hei")
        println("Fant uuid: $uuid")
        assertNotNull(testRedis.get(RedisKey.of(uuid, EventName.FORESPØRSEL_LAGRET)))
        println(testRedis.exist(*arrayOf("uuid", "uuid2")))
        val message2 = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET.name,
                Key.FORESPOERSEL_ID.str to foresporselId,
                Key.UUID.str to uuid,
                Key.FAIL.str to Behov.create(EventName.FORESPØRSEL_LAGRET, BehovType.FULLT_NAVN, foresporselId.toString()).createFail("Klarte ikke hente navn")
                // Key.FAIL.str to "Klarte ikke hente navn"
            )
        )
        testRapid.sendTestMessage(
            message2.toJson()
        )
        assertNotNull(testRedis.get(RedisKey.of(uuid + "arbeidstakerInformasjon")))
    }
}
