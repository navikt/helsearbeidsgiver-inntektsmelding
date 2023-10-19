package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import java.util.UUID

class BehovTest {

    val behov = BehovType.FULLT_NAVN
    val event = EventName.FORESPØRSEL_LAGRET

    @Test
    fun createFail() {
        val forespoerselId = UUID.randomUUID()

        val utloesendeMelding = JsonMessage.newMessage(
            mapOf(
                Key.BEHOV.str to behov,
                Key.EVENT_NAME.str to event,
                "hepp" to "hei"
            )
        )

        val behov = Behov(
            event = event,
            behov = behov,
            forespoerselId = forespoerselId.toString(),
            jsonMessage = utloesendeMelding
        )
        val feilmelding = "feilmelding"
        val fail = behov.createFail(feilmelding)

        fail shouldBe Fail(
            feilmelding = feilmelding,
            event = event,
            transaksjonId = null,
            forespoerselId = forespoerselId,
            utloesendeMelding = utloesendeMelding.toJson().parseJson()
        )
    }
}
