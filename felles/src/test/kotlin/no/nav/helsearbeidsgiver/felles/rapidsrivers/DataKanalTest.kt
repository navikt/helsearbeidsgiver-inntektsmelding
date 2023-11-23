package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.mockk.clearAllMocks
import io.mockk.verify
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class DataKanalTest {

    private val testRapid: TestRapid = TestRapid()

    private val mockRedis = MockRedis()

    private val dummyListener = object : River.PacketListener {
        override fun onPacket(packet: JsonMessage, context: MessageContext) {}
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
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
        StatefullDataKanal(testFelter, EventName.INNTEKTSMELDING_MOTTATT, dummyListener, testRapid, mockRedis.store)
        val uuid = UUID.randomUUID()
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

        verify {
            mockRedis.store.set(RedisKey.of(uuid, DataFelt.FNR), "mytestfield")
        }
        verify(exactly = 0) {
            mockRedis.store.set(RedisKey.of(uuid, DataFelt.INNTEKT), any())
        }

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

        verify {
            mockRedis.store.set(RedisKey.of(uuid, DataFelt.FNR), "mytestfield")
            mockRedis.store.set(RedisKey.of(uuid, DataFelt.INNTEKT), "mytestfield2")
        }
    }

    @Test
    fun `Test DATA collection, Object`() {
        val testFelter = arrayOf(DataFelt.ARBEIDSGIVER_INFORMASJON)
        StatefullDataKanal(testFelter, EventName.INNTEKTSMELDING_MOTTATT, dummyListener, testRapid, mockRedis.store)
        val uuid = UUID.randomUUID()
        val personDato = PersonDato("X", null, "")

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.UUID to uuid.toJson(),
            Key.DATA to "".toJson(),
            DataFelt.ARBEIDSGIVER_INFORMASJON to personDato.toJson(PersonDato.serializer())
        )

        verify {
            mockRedis.store.set(RedisKey.of(uuid, DataFelt.ARBEIDSGIVER_INFORMASJON), personDato.toJsonStr(PersonDato.serializer()))
        }
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
