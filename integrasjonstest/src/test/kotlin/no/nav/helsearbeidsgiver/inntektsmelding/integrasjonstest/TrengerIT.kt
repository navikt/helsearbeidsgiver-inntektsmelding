package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.mockk.every
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.toHentTrengerImLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarMedSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Innsending av skjema fra frontend")
class TrengerIT : EndToEndTest() {

    private val FNR = "fnr-123"
    private val ORGNR = "orgnr-456"
    private val SAK_ID = "sak_id_123"
    private val OPPGAVE_ID = "oppgave_id_456"
    private val FORESPØRSEL_ID = UUID.randomUUID().toString()
    private val INITIATED_ID = UUID.randomUUID().toString()
    private val CLIENT_ID = UUID.randomUUID().toString()

    private fun setup() {
        forespoerselRepository.lagreForespørsel(FORESPØRSEL_ID, ORGNR)
        forespoerselRepository.oppdaterSakId(SAK_ID, FORESPØRSEL_ID)
        forespoerselRepository.oppdaterOppgaveId(FORESPØRSEL_ID, OPPGAVE_ID)
    }

    @Test
    @Disabled
    fun `Test trengerIM meldingsflyt`() {
        var transactionID = ""
        every {
            priProducer.send(any())
        } answers { true }

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                Key.CLIENT_ID.str to CLIENT_ID,
                DataFelt.FORESPOERSEL_ID.str to FORESPØRSEL_ID,
                Key.BOOMERANG to mapOf(
                    Key.INITIATE_ID.str to INITIATED_ID,
                    Key.INITIATE_EVENT.str to EventName.TRENGER_REQUESTED.name
                )
            )
        )

        Thread.sleep(10000)

        with(filter(EventName.TRENGER_REQUESTED, behovType = BehovType.HENT_TRENGER_IM).first()) {
            // Ble lagret i databasen
            transactionID = this[Key.UUID.str].asText()
        }

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.name,
                Key.DATA.str to "",
                Key.UUID.str to transactionID,
                DataFelt.FORESPOERSEL_SVAR.str to mockForespoerselSvarMedSuksess().toHentTrengerImLøsning().value!!.toJsonStr(TrengerInntekt.serializer())
            )
        )

        Thread.sleep(12000)

        with(filter(EventName.TRENGER_REQUESTED, behovType = BehovType.HENT_TRENGER_IM).first()) {
            // Ble lagret i databasen
            Assertions.assertEquals(transactionID, this[Key.UUID.str].asText())
        }

        with(filter(EventName.TRENGER_REQUESTED, behovType = BehovType.VIRKSOMHET).first()) {
            // Ble lagret i databasen
            Assertions.assertEquals(transactionID, this[Key.UUID.str].asText())
        }

        with(filter(EventName.TRENGER_REQUESTED, behovType = BehovType.FULLT_NAVN).first()) {
            // Ble lagret i databasen
            Assertions.assertEquals(transactionID, this[Key.UUID.str].asText())
        }

        with(filter(EventName.TRENGER_REQUESTED, behovType = BehovType.INNTEKT).first()) {
            // Ble lagret i databasen
            Assertions.assertEquals(transactionID, this[Key.UUID.str].asText())
        }

        val trengerResultatJson = redisStore.get(RedisKey.of(CLIENT_ID))
        println("In test $trengerResultatJson")
        val objekt = trengerResultatJson?.fromJson(TrengerData.serializer())
        println(objekt)
    }
}
