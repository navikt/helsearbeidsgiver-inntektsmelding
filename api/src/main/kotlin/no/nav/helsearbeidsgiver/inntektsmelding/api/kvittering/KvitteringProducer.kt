package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger

class KvitteringProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter KvitteringProducer...")
    }

    fun publish(foresporselId: String): String {
        val packet: JsonMessage = JsonMessage.newMessage(
            mapOf(
                Key.EVENT_NAME.str to EventName.HENT_KVITTERING.name,
                Key.UUID.str to foresporselId
            )
        )
        rapid.publish(packet.toJson())
        logger.info("Publiserte kvitteringBehov id=$foresporselId")
        return foresporselId
    }
}
