package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import com.fasterxml.jackson.module.kotlin.contains
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsnending er mottatt`() {
        val forespoerselId = UUID.randomUUID().toString()
        val clientID = UUID.randomUUID().toString()
        forespoerselRepository.lagreForespørsel(forespoerselId, TestData.validOrgNr)
        var transaksjonsID: String? = null
        this.filterMessages = {
            val eventName = it.get(Key.EVENT_NAME.str).asText()
            if (it.contains(Key.CLIENT_ID.str)) {
                assertEquals(clientID,it[Key.CLIENT_ID.str].asText())
                true
            } else {
                if (transaksjonsID == null && (eventName == EventName.INSENDING_STARTED.name && it.contains(Key.BEHOV.str))) {
                    transaksjonsID = it[Key.UUID.str].asText()
                }
                val msgUuid = if (it.contains(Key.UUID.str)) it.get(Key.UUID.str).asText() else it.get(Key.TRANSACTION_ORIGIN.str).asText()
                msgUuid == transaksjonsID && (
                    eventName == EventName.INSENDING_STARTED.name ||
                        (eventName == EventName.INNTEKTSMELDING_MOTTATT.name && !it.has(Key.BEHOV.str))
                    ) && !it.has(
                    Key.LØSNING.str
                )
            }
        }

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                Key.CLIENT_ID.str to clientID,
                DataFelt.INNTEKTSMELDING.str to GYLDIG_INNSENDING_REQUEST,
                DataFelt.ORGNRUNDERENHET.str to TestData.validOrgNr,
                Key.IDENTITETSNUMMER.str to TestData.validIdentitetsnummer,
                Key.FORESPOERSEL_ID.str to forespoerselId
            )
        )
        Thread.sleep(10000)
        assertEquals(getMessageCount(), 9) {
            "Message count was " + getMessageCount()
        }
    }
}
