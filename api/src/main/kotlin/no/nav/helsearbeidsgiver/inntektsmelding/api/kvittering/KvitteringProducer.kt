package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import java.util.UUID

class KvitteringProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter KvitteringProducer...")
    }

    fun publish(foresporselId: String): String {
        val transaksjonsId = UUID.randomUUID().toString()
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.KVITTERING_REQUESTED.name,
                Key.UUID.str to foresporselId,
                Key.INITIATE_ID.str to transaksjonsId
            )
        )
        rapid.publish(packet.toJson())
        logger.info("Publiserte kvittering requested, forespørselid=$foresporselId")
        return transaksjonsId
    }
}
