package no.nav.helsearbeidsgiver.felles.rapidsrivers

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID

class DataKanalTest {

    val testRapid: TestRapid = TestRapid()

    val redis = MockRedisStore()

    val dummyListener = object : River.PacketListener {
        override fun onPacket(packet: JsonMessage, context: MessageContext) {
        }
    }

    @Test
    fun `DATA packet bør vare fanget`() {
        val testDataKanal = TestDataKanal(testRapid)
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.DATA.str to "",
                    Key.UUID.str to "123",
                    DataFelt.SAK_ID.str to "sak_id_1"
                )
            ).toJson()
        )
        assert(testDataKanal.invocations == 1)
    }

    @Test
    fun `FAIL,EVENT packet ikke fanget`() {
        val testDataKanal = TestDataKanal(testRapid)
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to "123",
                    DataFelt.SAK_ID.str to "sak_id_1"
                )
            ).toJson()
        )
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.FAIL.str to "",
                    Key.UUID.str to "123",
                    DataFelt.SAK_ID.str to "sak_id_1"
                )
            ).toJson()
        )
        assert(testDataKanal.invocations == 0)
    }

    @Test
    fun `Test DATA collection, Primitive`() {
        val testFelter = arrayOf("TESTFELT1", "TESTFELT2")
        val statefullDataKanal = StatefullDataKanal(testFelter, EventName.INNTEKTSMELDING_MOTTATT, dummyListener, testRapid, redis)
        val uuid = UUID.randomUUID().toString()
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to uuid,
                    Key.DATA.str to "",
                    "TESTFELT1" to "mytestfield"
                )
            ).toJson()
        )
        Assertions.assertEquals("mytestfield", redis.get(uuid + "TESTFELT1"))
        Assertions.assertNull(redis.get(uuid + "TESTFELT2"))

        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to uuid,
                    Key.DATA.str to "",
                    "TESTFELT2" to "mytestfield2"
                )
            ).toJson()
        )
        Assertions.assertEquals("mytestfield", redis.get(uuid + "TESTFELT1"))
        Assertions.assertEquals("mytestfield2", redis.get(uuid + "TESTFELT2"))
    }

    @Test
    fun `Test DATA collection, Object`() {
        val testFelter = arrayOf("TESTFELT1", "TESTFELT2")
        val statefullDataKanal = StatefullDataKanal(testFelter, EventName.INNTEKTSMELDING_MOTTATT, dummyListener, testRapid, redis)
        val uuid = UUID.randomUUID().toString()
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf<String, Any>(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to uuid,
                    Key.DATA.str to "",
                    "TESTFELT1" to customObjectMapper().valueToTree(PersonDato("X", null))
                )
            ).toJson()
        )
        val personDato = customObjectMapper().readValue(redis.get(uuid + "TESTFELT1"), PersonDato::class.java)
        Assertions.assertEquals("X", personDato.navn)
    }
}

class TestDataKanal(rapidsConnection: RapidsConnection) : DataKanal(rapidsConnection) {
    override val eventName: EventName = EventName.INNTEKTSMELDING_MOTTATT
    var invocations = 0

    override fun accept(): River.PacketValidation = River.PacketValidation { }

    override fun onData(packet: JsonMessage) {
        ++invocations
    }
}
