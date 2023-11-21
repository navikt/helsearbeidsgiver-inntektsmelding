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
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
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
        val testFelter = arrayOf(DataFelt.FNR, DataFelt.INNTEKT)
        StatefullDataKanal(testFelter, EventName.INNTEKTSMELDING_MOTTATT, dummyListener, testRapid, redis)
        val uuid = UUID.randomUUID().toString()
        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to uuid,
                    Key.DATA.str to "",
                    DataFelt.FNR.str to "mytestfield"
                )
            ).toJson()
        )
        Assertions.assertEquals("mytestfield", redis.get(uuid + DataFelt.FNR.str))
        Assertions.assertNull(redis.get(uuid + DataFelt.INNTEKT.str))

        testRapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_MOTTATT.name,
                    Key.UUID.str to uuid,
                    Key.DATA.str to "",
                    DataFelt.INNTEKT.str to "mytestfield2"
                )
            ).toJson()
        )
        Assertions.assertEquals("mytestfield", redis.get(uuid + DataFelt.FNR.str))
        Assertions.assertEquals("mytestfield2", redis.get(uuid + DataFelt.INNTEKT.str))
    }

    @Test
    fun `Test DATA collection, Object`() {
        val testFelter = arrayOf(DataFelt.ARBEIDSGIVER_INFORMASJON)
        StatefullDataKanal(testFelter, EventName.INNTEKTSMELDING_MOTTATT, dummyListener, testRapid, redis)
        val uuid = UUID.randomUUID().toString()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.UUID to uuid.toJson(),
            Key.DATA to "".toJson(),
            DataFelt.ARBEIDSGIVER_INFORMASJON to PersonDato("X", null, "").toJson(PersonDato.serializer())
        )

        val personDato = redis.get(uuid + DataFelt.ARBEIDSGIVER_INFORMASJON.str)?.fromJson(PersonDato.serializer())

        Assertions.assertEquals("X", personDato?.navn)
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
